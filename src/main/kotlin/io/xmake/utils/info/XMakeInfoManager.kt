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

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.messages.Topic
import io.xmake.file.highlight.XMakeLuaLexer
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.XMakeBuildProfileManager
import io.xmake.project.profile.XMakeBuildProfileOptions
import io.xmake.project.profile.xmakeBuildProfileOptionsCache
import io.xmake.project.toolkit.ToolkitListener
import io.xmake.run.command.configureBestEffort
import io.xmake.run.command.executeInfoQuery
import io.xmake.run.command.withProfileCommands
import io.xmake.run.target.activeOrSingleXMakeBuildProfile
import io.xmake.utils.SystemUtils
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

    val xmakeInfo: XMakeInfo = XMakeInfo()

    private val messageBusConnection = project.messageBus.connect(this)
    private val probeRequests = Channel<XMakeBuildProfile>(Channel.CONFLATED)

    init {
        messageBusConnection.subscribe(
            ToolkitListener.TOPIC,
            object : ToolkitListener {
                override fun toolkitsChanged() {
                    project.xmakeBuildProfileOptionsCache.clear()
                    probeActiveBuildProfile()
                }
            },
        )
        messageBusConnection.subscribe(
            XMakeBuildProfileManager.TOPIC,
            XMakeBuildProfileManager.Listener {
                project.xmakeBuildProfileOptionsCache.clear()
                probeActiveBuildProfile()
            },
        )
        messageBusConnection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    val xmakeProjectAppearedOrChanged = events.any { event ->
                        (event is VFileCreateEvent ||
                                event is VFileContentChangeEvent ||
                                event is VFilePropertyChangeEvent) &&
                                event.file?.name?.equals("xmake.lua", ignoreCase = true) == true
                    }
                    if (xmakeProjectAppearedOrChanged) probeActiveBuildProfile()
                    if (xmakeProjectAppearedOrChanged) project.xmakeBuildProfileOptionsCache.clear()
                }
            },
        )
        scope.launch {
            probeRequests.consumeAsFlow()
                .debounce(PROBE_DEBOUNCE)
                .collect { profile -> probeBuildProfile(profile) }
        }
    }

    fun probeActiveBuildProfile() {
        if (project.isDisposed || !SystemUtils.isXMakeProject(project)) return
        val profile = project.activeOrSingleXMakeBuildProfile ?: return
        probeRequests.trySend(profile)
    }

    private suspend fun probeBuildProfile(profile: XMakeBuildProfile) {
        try {
            withContext(Dispatchers.IO) {
                    project.withProfileCommands(profile) {
                        configureBestEffort(it)
                        val parser = XMakeInfo()
                        val architectures = parser.parseArchitectures(executeInfoQuery("architectures", it))
                        val buildModes = parser.parseBuildModes(executeInfoQuery("buildmodes", it))
                        val platforms = parser.parsePlatforms(executeInfoQuery("platforms", it))
                        val targets = parser.parseTargets(executeInfoQuery("targets", it))
                        val toolchains = parser.parseToolchains(executeInfoQuery("toolchains", it))
                        val apis = parser.parseApis(executeInfoQuery("apis", it))
                        xmakeInfo.apply {
                            this.architectures = architectures
                            this.buildModes = buildModes
                            this.platforms = platforms
                            this.targets = targets
                            this.toolchains = toolchains
                            this.apis = apis
                        }
                        project.xmakeBuildProfileOptionsCache.put(
                            profile,
                            XMakeBuildProfileOptions(
                                architectures = architectures,
                                buildModes = buildModes,
                                platforms = platforms,
                                toolchains = toolchains,
                            ),
                        )

                    if (xmakeInfo.apis.isNotEmpty()) {
                        XMakeLuaLexer.updateApis(xmakeInfo.apis)
                    }
                    project.messageBus.syncPublisher(XMAKE_INFO_TOPIC).onXMakeInfoUpdated(xmakeInfo)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warn("Failed to probe XMake information for profile ${profile.id}", error)
        }
    }

    override fun dispose() {
        messageBusConnection.disconnect()
    }

    interface XMakeInfoListener {
        fun onXMakeInfoUpdated(xmakeInfo: XMakeInfo)
    }

    companion object {
        private val PROBE_DEBOUNCE = 300.milliseconds

        val Log = logger<XMakeInfoManager>()
        val XMAKE_INFO_TOPIC = Topic.create("XMake Info Updated", XMakeInfoListener::class.java)

        fun getInstance(project: Project): XMakeInfoManager =
            project.getService(XMakeInfoManager::class.java) ?: error("Failed to get XMakeInfoManager for $project")
    }
}
