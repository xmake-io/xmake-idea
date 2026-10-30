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
import com.intellij.util.messages.Topic
import io.xmake.file.highlight.XMakeApiRegistry
import io.xmake.project.directory.XMakeProjectDirectoryManager
import io.xmake.project.directory.XMakeProjectDirectoryResolutionService
import io.xmake.project.directory.hasRootXMakeLua
import io.xmake.project.directory.hasXMakeProjectDirectorySource
import io.xmake.project.directory.isXMakeLuaVfsEvent
import io.xmake.project.profile.XMakeBuildOptions
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.XMakeBuildProfileManager
import io.xmake.project.profile.queryXMakeBuildOptions
import io.xmake.project.target.discoverXMakeBuildTargets
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitInstallationKey
import io.xmake.project.toolkit.ToolkitListener
import io.xmake.project.toolkit.XMakeVersionRefusal
import io.xmake.project.toolkit.XMakeVersionRefusalException
import io.xmake.project.toolkit.installationKey
import io.xmake.run.command.withProfileCommands
import io.xmake.run.target.activeOrSingleXMakeBuildProfile
import io.xmake.utils.notify
import io.xmake.utils.publish
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
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

    /** The refusal per installation, so run configuration checks can refuse without querying. */
    private val versionRefusals = ConcurrentHashMap<ToolkitInstallationKey, XMakeVersionRefusal>()

    /** One proactive balloon per refused installation, whichever path answers first. */
    @Volatile
    private var notifiedRefusalKey: ToolkitInstallationKey? = null

    /** The installation the cached apis listing came from; remembered across toolkit switches,
     *  because the apis listing answers for the installation, not the project. */
    private var cachedApisKey: ToolkitInstallationKey? = null

    private val buildOptions = CacheLayer<ToolkitInstallationKey, XMakeBuildOptions>(scope)
    private val buildTargets = CacheLayer<XMakeBuildProfile, List<String>>(scope)

    private val messageBusConnection = project.messageBus.connect(this)
    private val probeRequests = Channel<XMakeBuildProfile>(Channel.CONFLATED)

    init {
        messageBusConnection.subscribe(
            ToolkitListener.TOPIC,
            ToolkitListener { invalidateCaches() },
        )
        messageBusConnection.subscribe(
            XMakeBuildProfileManager.TOPIC,
            XMakeBuildProfileManager.Listener { enqueueActiveProfileProbe() },
        )
        messageBusConnection.subscribe(
            XMakeProjectDirectoryManager.TOPIC,
            XMakeProjectDirectoryManager.Listener { invalidateProjectCaches() },
        )
        // A probe dropped while the cached directory awareness lagged behind is retried here.
        messageBusConnection.subscribe(
            XMakeProjectDirectoryResolutionService.TOPIC,
            XMakeProjectDirectoryResolutionService.Listener { invalidateProjectCaches() },
        )
        messageBusConnection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (events.any { isXMakeLuaVfsEvent(it, includeContentChanges = true) }) {
                        invalidateProjectCaches()
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

    /** Loads the build options for [toolkit]; the listings are profile-independent (see
     *  [queryXMakeBuildOptions]), so the cache is keyed by the installation. */
    internal suspend fun loadBuildOptions(profile: XMakeBuildProfile, toolkit: Toolkit): XMakeBuildOptions {
        val options = buildOptions.obtain(toolkit.installationKey()) {
            project.queryXMakeBuildOptions(profile, toolkit)
        }
        updateVersionRefusal(toolkit, options.versionRefusal)
        return options
    }

    internal fun versionRefusal(toolkit: Toolkit): XMakeVersionRefusal? =
        versionRefusals[toolkit.installationKey()]

    /** A null refusal clears the entry; the next one raises the balloon again. */
    private fun updateVersionRefusal(toolkit: Toolkit, refusal: XMakeVersionRefusal?) {
        val key = toolkit.installationKey()
        if (refusal == null) {
            versionRefusals.remove(key)
            if (notifiedRefusalKey == key) notifiedRefusalKey = null
        } else {
            versionRefusals[key] = refusal
            if (notifiedRefusalKey != key) {
                notifiedRefusalKey = key
                project.notify(message = refusal.message, title = "The project needs a newer xmake")
            }
        }
    }

    /** Loads the build targets of one profile; the discovery keeps its configure side effect,
     *  which a cache hit skips. */
    internal suspend fun loadBuildTargets(profile: XMakeBuildProfile): List<String> =
        buildTargets.obtain(profile) { project.discoverXMakeBuildTargets(it) }

    /** Drops every cached query result; the cached apis listing survives, because it answers
     *  for the installation, not the project. */
    private fun invalidateCaches() {
        buildOptions.invalidate()
        buildTargets.invalidate()
        if (!project.isDisposed) {
            project.publish(TOPIC) { cachesChanged() }
        }
        enqueueActiveProfileProbe()
    }

    /** The project script or directory changed; the cached apis listing drops too, because
     *  the version refusal answers for the project, not the installation. */
    private fun invalidateProjectCaches() {
        cachedApisKey = null
        invalidateCaches()
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
                    val probedApis = if (apisCached) {
                        xmakeInfo.apis
                    } else {
                        listing("apis", XMakeInfo::parseApis) ?: run {
                            // The apis listing is rejected before the project script loads; the
                            // toolchains listing loads the script and so can reveal the refusal.
                            listing("toolchains", XMakeInfo::parseToolchains)
                            previous.apis
                        }
                    }
                    val info = XMakeInfo(apis = probedApis)
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
                profile.resolveToolkit(project)?.let { updateVersionRefusal(it, null) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: XMakeVersionRefusalException) {
            Log.warn(error.message)
            updateVersionRefusal(error.refusal.toolkit, error.refusal)
        } catch (error: Exception) {
            Log.warn("Failed to probe XMake information for profile ${profile.id}", error)
        }
    }

    override fun dispose() {
        messageBusConnection.disconnect()
    }

    private fun apisCacheKey(profile: XMakeBuildProfile): ToolkitInstallationKey? =
        profile.toolkitId?.let { id -> ToolkitInstallationKey(id, profile.resolveToolkit(project)?.version) }

    fun interface Listener {
        fun cachesChanged()
    }

    companion object {
        private val PROBE_DEBOUNCE = 300.milliseconds

        private val Log = logger<XMakeInfoManager>()

        @Topic.ProjectLevel
        val TOPIC: Topic<Listener> = Topic.create(
            "XMake info caches changed",
            Listener::class.java,
        )

        fun getInstance(project: Project): XMakeInfoManager =
            project.getService(XMakeInfoManager::class.java) ?: error("Failed to get XMakeInfoManager for $project")
    }
}

/** One xmake installation at one version; the toolkit id already encodes the host and the
 *  installation path, so the version is the only extra dimension. A null version marks a
 *  toolkit that could not be resolved far enough to know it. */
/** One map of cached query results, where in-flight loads join and invalidation cancels
 *  pending loads and drops values by generation. */
private class CacheLayer<K : Any, R : Any>(
    private val scope: CoroutineScope,
) {
    private val lock = Any()
    private val values = HashMap<K, R>()
    private val pending = HashMap<K, Deferred<Result<R>>>()
    private var generation = 0L

    /** Loads the value for [key], joining an in-flight load; [load] is the first caller's one,
     *  so concurrent callers with different loaders share its result. */
    suspend fun obtain(key: K, load: suspend (K) -> R): R {
        while (true) {
            val startGeneration: Long
            val deferred = synchronized(lock) {
                values[key]?.let { return it }
                startGeneration = generation
                pending[key] ?: startLoad(key, load)
            }
            val value = try {
                deferred.await().getOrThrow()
            } catch (error: CancellationException) {
                if (!currentCoroutineContext().isActive || !scope.isActive) {
                    throw error
                }
                continue
            }
            if (synchronized(lock) { startGeneration == generation }) return value
        }
    }

    /** The load runs in [scope] and captures its own failure, so a deferred nobody awaits
     *  cannot leak its exception. */
    private fun startLoad(key: K, load: suspend (K) -> R): Deferred<Result<R>> {
        val startGeneration = generation
        val deferred = scope.async(start = CoroutineStart.LAZY) {
            val result = try {
                Result.success(load(key))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            result.getOrNull()?.let { value ->
                synchronized(lock) {
                    if (startGeneration == generation) {
                        values[key] = value
                    }
                }
            }
            result
        }
        pending[key] = deferred
        deferred.invokeOnCompletion {
            synchronized(lock) { pending.remove(key, deferred) }
        }
        deferred.start()
        return deferred
    }

    fun invalidate() {
        generation++
        val cancelled = synchronized(lock) {
            values.clear()
            pending.values.toList().also { pending.clear() }
        }
        cancelled.forEach { deferred -> deferred.cancel() }
    }
}
