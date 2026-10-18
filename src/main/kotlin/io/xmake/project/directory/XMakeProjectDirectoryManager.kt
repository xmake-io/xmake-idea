package io.xmake.project.directory

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import io.xmake.utils.publish
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.utils.extension.ToolkitHostExtension

/** Owns the persisted XMake project directory and publishes changes. Resolution policy lives in
 *  [XMakeProjectDirectoryResolver]. */
@Service(Service.Level.PROJECT)
@State(name = "XMakeProjectDirectory", storages = [Storage("xmake.xml")])
class XMakeProjectDirectoryManager(private val project: Project) :
    PersistentStateComponent<XMakeProjectDirectoryState> {

    private val stateLock = Any()
    private var currentState = XMakeProjectDirectoryState()

    /** The current state. Writes swap in freshly built instances and nothing mutates a State
     *  afterwards, so handing it out without copying is safe. */
    private val current: XMakeProjectDirectoryState
        get() = synchronized(stateLock) { currentState }

    override fun getState(): XMakeProjectDirectoryState = current.copyState()

    override fun loadState(state: XMakeProjectDirectoryState) {
        // Loading persisted state is not a user-visible change; migration paths publish explicitly.
        val normalized = state.normalized()
        synchronized(stateLock) {
            currentState = normalized
        }
    }

    /** Replaces the state from the settings page or the project wizard. */
    fun replaceState(state: XMakeProjectDirectoryState) {
        updateState { state }
    }

    private fun updateState(transform: (XMakeProjectDirectoryState) -> XMakeProjectDirectoryState) {
        val changed = synchronized(stateLock) {
            val normalized = transform(currentState).normalized()
            if (currentState == normalized) {
                false
            } else {
                currentState = normalized
                true
            }
        }
        if (changed) publishDirectoryChanged()
    }

    private fun publishDirectoryChanged() =
        project.publish(TOPIC) { projectDirectoryChanged() }

    /** Whether a directory source exists; resolution is delegated to the stateless resolver. */
    fun hasDirectorySource(): Boolean = resolver().hasDirectorySource()

    fun canResolve(toolkit: Toolkit): Boolean = resolver().canResolve(toolkit)

    fun resolveProjectDirectory(toolkit: Toolkit): String = resolver().resolveProjectDirectory(toolkit)

    fun resolveLocalSyncDirectory(): String = resolver().resolveLocalSyncDirectory()

    private fun resolver(): XMakeProjectDirectoryResolver {
        migrateSshHostIds()
        return XMakeProjectDirectoryResolver(project, current)
    }

    /** Rewrites entries persisted with a configuration id to the endpoint key.
     *  Idempotent; entries whose configuration is gone retry on the next resolution. */
    private fun migrateSshHostIds() {
        if (!current.hostDirectories.any { needsSshIdMigration(it.hostId) }) return
        val endpointByConfigId = ToolkitHostExtension.forHostType(ToolkitHostType.SSH)
            ?.getHosts(project).orEmpty()
            .filter { host -> host.type == ToolkitHostType.SSH && host.sshConfig != null && host.backendId != null }
            .associate { host -> host.sshConfig!!.id to host.backendId!! }
        if (endpointByConfigId.isEmpty()) return
        updateState { state ->
            state.copy(
                hostDirectories = state.hostDirectories.map { entry ->
                    val backendId = entry.hostId.removePrefix("${ToolkitHostType.SSH}:")
                    val endpoint = endpointByConfigId[backendId] ?: return@map entry
                    entry.copy(hostId = "${ToolkitHostType.SSH}:$endpoint")
                }.toMutableList(),
            )
        }
    }

    private fun needsSshIdMigration(hostId: String): Boolean =
        hostId.startsWith("${ToolkitHostType.SSH}:") && '@' !in hostId

    /** Imports directories previously owned by build profiles. First-wins: migration never
     *  overwrites a directory configured after the upgrade. */
    fun migrateLegacyProjectDirectories(legacyDirectories: List<LegacyProjectDirectory>) {
        updateState { state -> state.migrateLegacyDirectories(legacyDirectories) }
    }

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<Listener> = Topic.create("XMake project directory changed", Listener::class.java)

        fun getInstance(project: Project): XMakeProjectDirectoryManager =
            project.getService(XMakeProjectDirectoryManager::class.java)
                ?: error("Failed to get XMakeProjectDirectoryManager for $project")
    }

    fun interface Listener {
        fun projectDirectoryChanged()
    }
}

val Project.xmakeProjectDirectories: XMakeProjectDirectoryManager
    get() = XMakeProjectDirectoryManager.getInstance(this)
