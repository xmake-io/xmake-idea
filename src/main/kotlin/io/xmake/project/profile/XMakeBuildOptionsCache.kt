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
 * @file        XMakeBuildOptionsCache.kt
 *
 */
package io.xmake.project.profile

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.Topic
import io.xmake.project.directory.XMakeProjectDirectoryManager
import io.xmake.project.directory.XMakeProjectDirectoryResolutionService
import io.xmake.project.directory.isXMakeLuaVfsEvent
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitListener
import io.xmake.utils.publish
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/** Per-toolkit build options. The options come from `xmake show -l`, which reads `xmake.lua`
 *  and the xmake installation but not the profile's configured platform, so one catalog serves
 *  every profile that selects the same toolkit. */
@Service(Service.Level.PROJECT)
internal class XMakeBuildOptionsCache(
    private val project: Project,
    private val scope: CoroutineScope,
) : Disposable {
    private val lock = Any()
    private val optionsByToolkit = HashMap<ToolkitCacheKey, XMakeBuildOptions>()
    private val pendingLoads = HashMap<ToolkitCacheKey, Deferred<Result<XMakeBuildOptions>>>()
    private var generation = 0L
    private val messageBusConnection = project.messageBus.connect(this)

    init {
        messageBusConnection.subscribe(
            ToolkitListener.TOPIC,
            ToolkitListener { invalidate() },
        )
        messageBusConnection.subscribe(
            XMakeProjectDirectoryManager.TOPIC,
            XMakeProjectDirectoryManager.Listener { invalidate() },
        )
        messageBusConnection.subscribe(
            XMakeProjectDirectoryResolutionService.TOPIC,
            XMakeProjectDirectoryResolutionService.Listener { invalidate() },
        )
        messageBusConnection.subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    if (events.any { isXMakeLuaVfsEvent(it, includeContentChanges = true) }) {
                        invalidate()
                    }
                }
            },
        )
    }

    fun get(toolkit: Toolkit): XMakeBuildOptions? =
        synchronized(lock) { optionsByToolkit[key(toolkit)] }

    /** Loads the catalog for [toolkit], joining an in-flight load and caching a successful result. */
    suspend fun load(
        profile: XMakeBuildProfile,
        toolkit: Toolkit,
    ): XMakeBuildOptions {
        val key = key(toolkit)
        while (true) {
            val startGeneration: Long
            val deferred = synchronized(lock) {
                optionsByToolkit[key]?.let { return it }
                startGeneration = generation
                pendingLoads[key] ?: startLoad(key, profile, toolkit)
            }
            val options = try {
                deferred.await().getOrThrow()
            } catch (error: CancellationException) {
                if (!currentCoroutineContext().isActive || !scope.isActive || project.isDisposed) {
                    throw error
                }
                continue
            }
            if (synchronized(lock) { startGeneration == generation }) return options
        }
    }

    private fun startLoad(
        key: ToolkitCacheKey,
        profile: XMakeBuildProfile,
        toolkit: Toolkit,
    ): Deferred<Result<XMakeBuildOptions>> {
        val startGeneration = generation
        val deferred = scope.async(start = CoroutineStart.LAZY) {
            val result = try {
                Result.success(project.queryXMakeBuildOptions(profile, toolkit))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
            result.getOrNull()?.let { options ->
                synchronized(lock) {
                    if (startGeneration == generation) {
                        optionsByToolkit[key] = options
                    }
                }
            }
            result
        }
        pendingLoads[key] = deferred
        deferred.invokeOnCompletion {
            synchronized(lock) { pendingLoads.remove(key, deferred) }
        }
        deferred.start()
        return deferred
    }

    private fun invalidate() {
        generation++
        val cancelled = synchronized(lock) {
            optionsByToolkit.clear()
            pendingLoads.values.toList().also { pendingLoads.clear() }
        }
        cancelled.forEach { deferred -> deferred.cancel() }
        if (!project.isDisposed) {
            project.publish(TOPIC) { catalogChanged() }
        }
    }


    override fun dispose() {}

    fun interface Listener {
        fun catalogChanged()
    }

    /** One toolkit's cache slot. The toolkit id already encodes the host and the installation
     *  path, so the version is the only extra dimension. */
    internal data class ToolkitCacheKey(
        val toolkitId: String,
        val version: String,
    )

    fun key(toolkit: Toolkit): ToolkitCacheKey = ToolkitCacheKey(toolkit.id, toolkit.version)

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<Listener> = Topic.create(
            "XMake build options changed",
            Listener::class.java,
        )
    }
}

internal val Project.xmakeBuildOptions: XMakeBuildOptionsCache
    get() = serviceOrNull() ?: error("Failed to get XMakeBuildOptionsCache for $this")
