/*!A Xmake integration in IntelliJ IDEA/Clion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2015-present, Xmake Open Source Community.
 *
 * @author      ruki
 * @file        XMakeInfoManager.kt
 *
 */
package io.xmake.utils.info

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import io.xmake.file.highlight.XMakeApiRegistry
import io.xmake.project.directory.XMakeProjectDirectoryManager
import io.xmake.project.directory.XMakeProjectDirectoryResolutionService
import io.xmake.project.directory.hasRootXMakeLua
import io.xmake.project.directory.hasXMakeProjectDirectorySource
import io.xmake.project.directory.isXMakeLuaVfsEvent
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.XMakeBuildProfileManager
import io.xmake.project.toolkit.ToolkitListener
import io.xmake.run.command.XMakeProjectVersionError
import io.xmake.run.command.userMessage
import io.xmake.run.command.withProfileCommands
import io.xmake.run.target.activeOrSingleXMakeBuildProfile
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class XMakeInfoManager(
    val project: Project,
    scope: CoroutineScope,
) : Disposable {

    /** Latest probe result; replaced atomically so readers never see a torn snapshot. */
    @Volatile
    var xmakeInfo: XMakeInfo = XMakeInfo()
        private set

    private var probedToolkitId: String? = null

    private var cachedApisKey: Pair<String, String?>? = null

    private val messageBusConnection = project.messageBus.connect(this)
    private val probeRequests = Channel<XMakeBuildProfile>(Channel.CONFLATED)

    init {
        messageBusConnection.subscribe(
            ToolkitListener.TOPIC,
            ToolkitListener { refreshProfileInfo() },
        )
        messageBusConnection.subscribe(
            XMakeBuildProfileManager.TOPIC,
            XMakeBuildProfileManager.Listener { refreshProfileInfo() },
        )
        messageBusConnection.subscribe(
            XMakeProjectDirectoryManager.TOPIC,
            XMakeProjectDirectoryManager.Listener { refreshProfileInfo() },
        )
        // A probe dropped while the cached directory awareness lagged behind is retried here.
        messageBusConnection.subscribe(
            XMakeProjectDirectoryResolutionService.TOPIC,
            XMakeProjectDirectoryResolutionService.Listener { refreshProfileInfo() },
        )
        messageBusConnection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (events.any { isXMakeLuaVfsEvent(it, includeContentChanges = true) }) {
                        enqueueActiveProfileProbe()
                    }
                }
            },
        )
        scope.launch {
            probeRequests.consumeAsFlow()
                .debounce(PROBE_DEBOUNCE)
                .collect { profile -> probeBuildProfile(profile) }
        }
    }

    /** Recomputes everything derived from the active profile. No-op while unresolved; xmakeInfo
     *  then keeps its last values until the next successful resolve. */
    private fun refreshProfileInfo() {
        // Directory changes can arrive before cached action awareness is refreshed.
        enqueueActiveProfileProbe()
    }

    fun probeActiveBuildProfile() {
        if (project.isDisposed || !project.hasXMakeProjectDirectorySource) return
        enqueueActiveProfileProbe()
    }

    private fun enqueueActiveProfileProbe() {
        if (project.isDisposed) return
        val profile = project.activeOrSingleXMakeBuildProfile ?: return
        probeRequests.trySend(profile)
    }

    private suspend fun probeBuildProfile(profile: XMakeBuildProfile) {
        try {
            withContext(Dispatchers.IO) {
                // Probes without a usable project directory only produce noise; the cached flag
                // may lag a just-configured directory, so the IDE root is checked on disk too.
                if (!project.hasXMakeProjectDirectorySource && !project.hasRootXMakeLua) {
                    return@withContext
                }
                project.withProfileCommands(profile) {
                    val previous = if (profile.toolkitId == probedToolkitId) xmakeInfo else XMakeInfo()
                    probedToolkitId = profile.toolkitId
                    val apisKey = apisCacheKey(profile)
                    val apisCached = apisKey != null && apisKey == cachedApisKey && xmakeInfo.apis.isNotEmpty()
                    val info = XMakeInfo(
                        apis = if (apisCached) {
                            xmakeInfo.apis
                        } else {
                            listing("apis", XMakeInfo::parseApis) ?: previous.apis
                        },
                    )
                    if (info.apis.isNotEmpty()) {
                        cachedApisKey = apisKey
                    }
                    xmakeInfo = info

                    // The Lua lexer reads the registry, so editors opened before the probe keep
                    // the old vocabulary unless the analyzer restarts.
                    if (info.apis.isNotEmpty() && info.apis != XMakeApiRegistry.current) {
                        XMakeApiRegistry.update(info.apis)
                        DaemonCodeAnalyzer.getInstance(project).restart("Xmake api set changed")
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: XMakeProjectVersionError) {
            val toolkit = profile.resolveToolkit(project)
            if (toolkit != null) {
                Log.warn(error.userMessage(toolkit))
            }
        } catch (error: Exception) {
            Log.warn("Failed to probe XMake information for profile ${profile.id}", error)
        }
    }

    override fun dispose() {
        messageBusConnection.disconnect()
    }

    private fun apisCacheKey(profile: XMakeBuildProfile): Pair<String, String?>? =
        profile.toolkitId?.let { id -> id to profile.resolveToolkit(project)?.version }

    companion object {
        private val PROBE_DEBOUNCE = 300.milliseconds

        private val Log = logger<XMakeInfoManager>()

        fun getInstance(project: Project): XMakeInfoManager =
            project.getService(XMakeInfoManager::class.java) ?: error("Failed to get XMakeInfoManager for $project")
    }
}
