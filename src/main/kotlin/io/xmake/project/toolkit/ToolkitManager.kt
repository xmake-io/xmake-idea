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
 * @file        ToolkitManager.kt
 *
 */
package io.xmake.project.toolkit

import com.intellij.execution.wsl.WSLUtil
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection
import io.xmake.project.toolkit.ToolkitHostType.LOCAL
import io.xmake.project.toolkit.ToolkitHostType.SSH
import io.xmake.project.toolkit.ToolkitHostType.WSL
import io.xmake.utils.extension.ToolkitHostExtension
import io.xmake.utils.runCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Application-level toolkit registrations, resolved against a project's hosts. */
@Service(Service.Level.APP)
@State(name = "toolkits", storages = [Storage("xmakeToolkits.xml")])
class ToolkitManager(scope: CoroutineScope) :
    PersistentStateComponent<ToolkitManager.State>,
    Disposable {

    /** Persisted XML shape of toolkit registrations. */
    class State {
        @XCollection(propertyElementName = "registeredToolKits")
        val registeredToolkits = mutableSetOf<Toolkit>()

        var defaultToolkitId: String? = null

        /** Compatibility inputs for options persisted by older plugin versions. */
        var lastSelectedToolkitId: String? = null

        var preferredToolkitId: String? = null
    }

    private val stateLock = Any()
    private val registry = ToolkitRegistry()
    private val detector = ToolkitDetector()
    private val pendingDetectionProjects = LinkedHashSet<Project?>()
    private val pendingDetectionLock = Any()
    private val detectionSignal = Channel<Unit>(Channel.CONFLATED)
    private val detectionLoop: Job

    init {
        detectionLoop = scope.launch {
            while (true) {
                val signal = detectionSignal.receiveCatching()
                if (signal.isClosed) break
                val projects = synchronized(pendingDetectionLock) {
                    pendingDetectionProjects.toList().also { pendingDetectionProjects.clear() }
                }
                projects.forEach { project -> runDetection(project) }
            }
        }
    }

    override fun dispose() {
        detectionSignal.close()
        detectionLoop.cancel()
    }

    internal var defaultToolkitId: String?
        get() = synchronized(stateLock) { registry.defaultToolkitId }
        set(value) {
            synchronized(stateLock) { registry.defaultToolkitId = value }
        }

    /** Registered and detected installations, resolved against [project]'s hosts. */
    fun toolkits(project: Project? = null): List<Toolkit> = toolkitsView(hostsFor(project))

    private fun toolkitsView(hosts: Map<ToolkitHostType, List<ToolkitHost>>): List<Toolkit> {
        val registered: List<Toolkit>
        val detected: List<Toolkit>
        val paths: Map<ToolkitHost.Id, Set<String>>
        synchronized(stateLock) {
            registered = registry.toolkits
            detected = detector.toolkits
            paths = detector.detectedPaths
        }

        val detectedByLocation = detected.associateBy(Toolkit::location)
        val merged = linkedMapOf<Toolkit.Location, Toolkit>()

        registered.forEach { toolkit ->
            val typeKnown = hosts[toolkit.host.type] != null
            val host = if (typeKnown) findToolkitHost(hosts, toolkit) else null
            val location = Toolkit.Location(host?.id ?: toolkit.host.id, toolkit.path)
            if (location in merged) return@forEach
            val match = detectedByLocation[location]
            merged[location] = (match ?: toolkit).copy(
                id = toolkit.id,
                host = host ?: toolkit.host,
                isRegistered = true,
                isAvailable = if (typeKnown) {
                    host != null &&
                            (match?.isAvailable
                                ?: paths[host.id]?.contains(toolkit.path)
                                ?: true)
                } else {
                    match?.isAvailable ?: toolkit.isAvailable
                },
            )
        }

        detected.forEach { toolkit ->
            val known = hosts[toolkit.host.type]
            if (known != null && known.none { it.id == toolkit.host.id }) return@forEach
            merged.putIfAbsent(toolkit.location, toolkit.copy(isRegistered = false))
        }

        return merged.values.toList()
    }

    fun registeredToolkits(project: Project? = null): List<Toolkit> =
        toolkits(project).filter(Toolkit::isRegistered)

    fun registeredToolkit(id: String, project: Project? = null): Toolkit? =
        toolkits(project).firstOrNull { toolkit -> toolkit.id == id && toolkit.isRegistered }

    fun defaultToolkit(project: Project? = null): Toolkit? =
        defaultToolkitId?.let { id -> registeredToolkit(id, project) }

    /** The toolkit to launch when the user has not chosen one; never null. */
    fun defaultRunnableToolkit(project: Project? = null): Toolkit =
        defaultToolkit(project)?.takeIf { it.isAvailable && !it.requiresBackend }
            ?: toolkits(project).firstOrNull { toolkit ->
                toolkit.isAvailable && !toolkit.requiresBackend
            }
            ?: PATH_FALLBACK_TOOLKIT

    /** The registered toolkit to preselect; null when nothing is registered. */
    fun defaultRegisteredToolkit(project: Project? = null): Toolkit? {
        val registered = registeredToolkits(project)
        return defaultToolkit(project)?.takeIf(Toolkit::isAvailable)
            ?: registered.firstOrNull(Toolkit::isAvailable)
            ?: registered.firstOrNull()
    }

    fun isRegistered(id: String): Boolean = synchronized(stateLock) { registry.find(id) != null }

    /** Registers a detected installation, or returns the registration on the same location. */
    fun register(id: String, project: Project? = null): Toolkit? {
        val hosts = hostsFor(project)
        var changed = false
        val registeredId: String? = synchronized(stateLock) {
            registry.find(id)?.let { registered -> return@synchronized registered.id }
            val detected = detector.toolkits.firstOrNull { toolkit -> toolkit.id == id }
                ?: return@synchronized null
            val host = findToolkitHost(hosts, detected) ?: return@synchronized null
            val candidate = detected.copy(host = host.toPersistedHost())
            val location = Toolkit.Location(host.id, candidate.path)
            val existing = registry.toolkits.firstOrNull { registered ->
                val registeredHost = findToolkitHost(hosts, registered)
                Toolkit.Location(registeredHost?.id ?: registered.host.id, registered.path) == location
            }
            if (existing != null) {
                existing.id
            } else {
                registry.register(candidate)
                changed = true
                candidate.id
            }
        } ?: return null

        if (changed) publishToolkitsChanged()
        return toolkitsView(hosts).firstOrNull { toolkit -> toolkit.id == registeredId && toolkit.isRegistered }
    }

    fun unregister(id: String) {
        if (synchronized(stateLock) { registry.unregister(id) } == null) return
        publishToolkitsChanged()
    }

    fun detect(project: Project? = null) {
        if (project?.isDisposed == true) return
        synchronized(pendingDetectionLock) { pendingDetectionProjects.add(project) }
        detectionSignal.trySend(Unit)
    }

    override fun getState(): State = synchronized(stateLock) { registry.toState() }

    override fun loadState(state: State) {
        val migrated = migrateLegacyState(state)
        val kept = synchronized(stateLock) { registry.replaceState(migrated) }
        if (kept.size != migrated.registeredToolkits.size) {
            Log.warn(
                "Discarded ${migrated.registeredToolkits.size - kept.size} duplicate " +
                        "toolkit registrations at the same location",
            )
        }
    }

    private fun migrateLegacyState(state: State): State {
        val migrated = State()
        state.registeredToolkits.mapTo(migrated.registeredToolkits) { toolkit ->
            val backendId = when (toolkit.host.type) {
                LOCAL -> null
                SSH -> {
                    val persisted = toolkit.host.backendId ?: toolkit.host.migratedBackendId
                    if (persisted == null) {
                        ToolkitHost.parseSshEndpoint(toolkit.name)
                    } else {
                        ToolkitHost.parseSshEndpoint(persisted) ?: persisted
                    }
                }
                WSL -> toolkit.host.migratedBackendId
            }
            toolkit.copy(host = ToolkitHost(toolkit.host.type, backendId))
        }
        migrated.defaultToolkitId = state.defaultToolkitId
            ?: state.lastSelectedToolkitId
            ?: state.preferredToolkitId
        return migrated
    }

    private suspend fun runDetection(project: Project?) {
        try {
            val effectiveProject = project?.takeUnless(Project::isDisposed)
            val hosts = withContext(Dispatchers.IO) { hostsFor(effectiveProject) }
            val normalized = synchronized(stateLock) { normalizeSshRegistrations(hosts) }
            if (normalized) publishToolkitsChanged()
            detector.detect(hosts.values.flatten(), effectiveProject).collect { result ->
                if (synchronized(stateLock) { detector.apply(result) }) publishToolkitsChanged()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.error("Failed to detect XMake toolkits", error)
        }
    }

    /** The hosts visible to [project]. A missing type key means the provider read failed for
     *  that type: callers keep that type's existing detection results and registered hosts and
     *  availability; an empty list means the type genuinely has no hosts. */
    private fun hostsFor(project: Project?): Map<ToolkitHostType, List<ToolkitHost>> {
        val effectiveProject = project?.takeUnless(Project::isDisposed)
        return buildMap {
            put(LOCAL, listOf(ToolkitHost(LOCAL)))
            if (WSLUtil.isSystemCompatible()) {
                runCancellable {
                    WslDistributionManager.getInstance().installedDistributions
                        .map { distribution -> ToolkitHost.wsl(distribution) }
                        .distinctBy(ToolkitHost::id)
                }.onSuccess { put(WSL, it) }
                    .onFailure { Log.warn("Failed to read WSL distributions", it) }
            } else {
                put(WSL, emptyList())
            }

            val ssh = ToolkitHostExtension.forHostType(SSH)
            if (ssh == null) {
                put(SSH, emptyList())
            } else {
                runCancellable { ssh.getHosts(effectiveProject).distinctBy(ToolkitHost::id) }
                    .onSuccess { put(SSH, it) }
                    .onFailure { Log.warn("Failed to read SSH hosts", it) }
            }
        }
    }

    /** Rewrites registrations that still carry an SSH configuration id or legacy name. Detection
     *  is a command with a project context, so this migration stays off the read path. */
    private fun normalizeSshRegistrations(hosts: Map<ToolkitHostType, List<ToolkitHost>>): Boolean {
        var changed = false
        registry.toolkits.forEach { toolkit ->
            if (toolkit.host.type != SSH) return@forEach
            val host = findToolkitHost(hosts, toolkit) ?: return@forEach
            if (host.backendId == null || toolkit.host.backendId == host.backendId) return@forEach
            val location = Toolkit.Location(host.id, toolkit.path)
            if (registry.toolkits.any { registered -> registered.id != toolkit.id && registered.location == location }) {
                return@forEach
            }
            if (registry.updateHost(toolkit.id, host) != null) changed = true
        }
        return changed
    }

    private fun publishToolkitsChanged() {
        val application = ApplicationManager.getApplication() ?: return
        if (application.isDisposed) return
        application.messageBus.syncPublisher(ToolkitListener.TOPIC).toolkitsChanged()
    }

    companion object {
        private val Log = logger<ToolkitManager>()

        private val PATH_FALLBACK_TOOLKIT = Toolkit(path = "xmake")

        fun getInstance(): ToolkitManager =
            serviceOrNull() ?: error("Failed to get ToolkitManager")
    }
}

/** Resolves a persisted toolkit host against the hosts visible to one project. */
internal fun findToolkitHost(
    hosts: Map<ToolkitHostType, List<ToolkitHost>>,
    toolkit: Toolkit,
): ToolkitHost? {
    val host = toolkit.host
    if (host.type == LOCAL) return host
    val known = hosts[host.type].orEmpty()
    return known.firstOrNull { candidate -> candidate.id == host.id }
        ?: known.firstOrNull { candidate -> candidate.sshConfig?.id == host.backendId }
        ?: ToolkitHost.parseSshEndpoint(toolkit.name)?.let { endpoint ->
            known.firstOrNull { candidate -> candidate.id == ToolkitHost.Id(SSH, endpoint) }
        }
        ?: known.firstOrNull { candidate ->
            host.backendId == null && candidate.backendId == toolkit.name
        }
}
