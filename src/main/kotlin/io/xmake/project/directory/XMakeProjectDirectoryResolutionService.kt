package io.xmake.project.directory

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.messages.Topic
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitListener
import io.xmake.project.toolkit.ToolkitManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/** Caches whether a project has an XMake directory source, so action updates on the EDT never
 *  touch the disk. The answer is recomputed when configured directories change or an xmake.lua
 *  is created, deleted, moved, or renamed directly. Moving or deleting an enclosing directory
 *  is not detected. */
@Service(Service.Level.PROJECT)
class XMakeProjectDirectoryResolutionService(
    private val project: Project,
    private val scope: CoroutineScope,
) : Disposable {

    /** The published snapshot; `null` until the first background computation completes. */
    @Volatile
    private var cachedResolution: DirectoryResolution? = null

    val hasDirectorySource: Boolean
        get() = cachedResolution?.hasDirectorySource == true

    /** Whether the IDE project root contains a root xmake.lua, from the latest snapshot. */
    val hasRootXMakeLua: Boolean
        get() = cachedResolution?.hasRootXMakeLua == true

    /** Cached, EDT-safe variant of [XMakeProjectDirectoryManager.canResolve] for action and
     *  execution-target updates. The answer is optimistic (true) until the first background
     *  computation completes; execution paths keep the authoritative disk validation. */
    fun isToolkitResolvable(toolkit: Toolkit): Boolean {
        if (toolkit.path.isBlank()) return false
        if (toolkit.requiresBackend && !toolkit.host.hasBackend) return false
        val resolution = cachedResolution ?: return true
        return toolkit.id in resolution.resolvableToolkitIds
    }

    private val initialResolution = CompletableDeferred<Unit>()
    private val requestGeneration = AtomicInteger()
    private val messageBusConnection = project.messageBus.connect(this)

    init {
        resolveAsync()
        messageBusConnection.subscribe(
            XMakeProjectDirectoryManager.TOPIC,
            XMakeProjectDirectoryManager.Listener { resolveAsync() },
        )
        messageBusConnection.subscribe(
            ToolkitListener.TOPIC,
            object : ToolkitListener {
                override fun toolkitsChanged() {
                    resolveAsync()
                }
            },
        )
        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                if (events.any(::isXMakeLuaVfsEvent)) resolveAsync()
            }
        })
    }

    private fun resolveAsync() {
        if (project.isDisposed) return
        val request = requestGeneration.incrementAndGet()
        scope.launch {
            try {
                val resolution = withContext(Dispatchers.IO) { resolveDirectories() }
                publishLatestResolution(request, resolution)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.error("Failed to resolve the XMake project directory source", error)
                // Escape the optimistic pre-first-result state; the info manager's disk probe
                // still recovers once a root xmake.lua appears.
                publishLatestResolution(request, cachedResolution ?: DirectoryResolution(false, false, emptySet()))
            } finally {
                // Startup awaits the first resolution: it must complete on every path.
                initialResolution.complete(Unit)
            }
        }
    }

    private fun resolveDirectories(): DirectoryResolution {
        val directories = project.xmakeProjectDirectories
        val resolvableToolkitIds = ToolkitManager.getInstance()
            .registeredToolkits(project)
            .filter { it.path.isNotBlank() }
            .filter { directories.canResolve(it) }
            .mapTo(mutableSetOf()) { it.id }
        return DirectoryResolution(directories.hasDirectorySource(), project.hasRootXMakeLua, resolvableToolkitIds)
    }

    /** A newer request supersedes an older result; only the newest result becomes visible. */
    private fun publishLatestResolution(request: Int, resolution: DirectoryResolution) {
        if (request != requestGeneration.get()) return
        if (!project.isDisposed) {
            val previous = cachedResolution
            cachedResolution = resolution
            // EDT readers may have acted on the previous snapshot (for example a profile form
            // that skipped its option query): tell them the answer changed.
            if (previous != resolution) publishResolutionUpdated()
        }
        initialResolution.complete(Unit)
    }

    private fun publishResolutionUpdated() {
        val publish = Runnable {
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(TOPIC).projectDirectoryResolutionUpdated()
            }
        }
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            publish.run()
        } else {
            application.invokeLater(publish)
        }
    }

    private data class DirectoryResolution(
        val hasDirectorySource: Boolean,
        val hasRootXMakeLua: Boolean,
        val resolvableToolkitIds: Set<String>,
    )

    companion object {
        private val Log = logger<XMakeProjectDirectoryResolutionService>()

        /** Announces that the cached resolution snapshot changed. Complements
         *  [XMakeProjectDirectoryManager.TOPIC], which reports configuration edits immediately:
         *  this topic fires only once the background resolution catches up, so EDT readers can
         *  re-query answers that were stale when the edit arrived. */
        @Topic.ProjectLevel
        val TOPIC: Topic<Listener> = Topic.create("XMake project directory resolution updated", Listener::class.java)
    }

    fun interface Listener {
        fun projectDirectoryResolutionUpdated()
    }

    /** Waits for the first resolution so project startup does not race the initial menu render. */
    suspend fun awaitInitialResolution() {
        initialResolution.await()
    }

    override fun dispose() {
        initialResolution.complete(Unit)
        // The message bus connection registered with [this] is disposed automatically.
    }
}


/** Whether the project has any XMake project-directory source. This is deliberately weaker than
 *  toolkit-specific resolution and is cached for action visibility. */
val Project.hasXMakeProjectDirectorySource: Boolean
    get() = getService(XMakeProjectDirectoryResolutionService::class.java)?.hasDirectorySource == true

/** Cached, EDT-safe answer to whether the project root contains a root xmake.lua. */
val Project.hasRootXMakeLuaCached: Boolean
    get() = getService(XMakeProjectDirectoryResolutionService::class.java)?.hasRootXMakeLua == true

/** Cached, EDT-safe answer to whether [toolkit] can resolve a project directory right now. */
fun Project.canResolveXMakeProjectDirectory(toolkit: Toolkit): Boolean =
    getService(XMakeProjectDirectoryResolutionService::class.java)
        ?.isToolkitResolvable(toolkit) == true

/** Completes only after the cached answer is ready, so startup orders menu availability. */
class XMakeProjectDirectoryResolutionActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(XMakeProjectDirectoryResolutionService::class.java)
            ?.awaitInitialResolution()
    }
}
