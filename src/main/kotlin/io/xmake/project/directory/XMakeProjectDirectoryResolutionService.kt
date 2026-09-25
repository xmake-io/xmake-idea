package io.xmake.project.directory

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
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

    @Volatile
    private var cachedHasDirectorySource = false

    val hasDirectorySource: Boolean
        get() = cachedHasDirectorySource

    private val initialResolution = CompletableDeferred<Unit>()
    private val requestGeneration = AtomicInteger()
    private val messageBusConnection = project.messageBus.connect(this)

    init {
        resolveAsync()
        messageBusConnection.subscribe(
            XMakeProjectDirectoryManager.TOPIC,
            XMakeProjectDirectoryManager.Listener { resolveAsync() },
        )
        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                if (events.any(::isRelevantVfsEvent)) resolveAsync()
            }
        })
    }

    private fun resolveAsync() {
        if (project.isDisposed) return
        val request = requestGeneration.incrementAndGet()
        scope.launch {
            val refreshed = withContext(Dispatchers.IO) {
                project.xmakeProjectDirectories.hasDirectorySource()
            }
            publishLatestResolution(request, refreshed)
        }
    }

    /** A newer request supersedes an older result; only the newest result becomes visible. */
    private fun publishLatestResolution(request: Int, refreshed: Boolean) {
        if (request != requestGeneration.get()) return
        if (!project.isDisposed) {
            cachedHasDirectorySource = refreshed
        }
        initialResolution.complete(Unit)
    }

    /** Waits for the first resolution so project startup does not race the initial menu render. */
    suspend fun awaitInitialResolution() {
        initialResolution.await()
    }

    // Deliberately over-inclusive: an xmake.lua outside the IDE project may back a configured
    // local directory, so filtering by project membership would miss relevant changes.
    private fun isRelevantVfsEvent(event: VFileEvent): Boolean =
        when (event) {
            is VFileCreateEvent, is VFileDeleteEvent, is VFileMoveEvent ->
                event.file?.name?.equals("xmake.lua", ignoreCase = true) == true

            is VFilePropertyChangeEvent ->
                event.propertyName == VirtualFile.PROP_NAME &&
                        listOf(event.oldValue, event.newValue).any { value ->
                            value is String && value.equals("xmake.lua", ignoreCase = true)
                        }

            else -> false
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

/** Completes only after the cached answer is ready, so startup orders menu availability. */
class XMakeProjectDirectoryResolutionActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(XMakeProjectDirectoryResolutionService::class.java)
            ?.awaitInitialResolution()
    }
}
