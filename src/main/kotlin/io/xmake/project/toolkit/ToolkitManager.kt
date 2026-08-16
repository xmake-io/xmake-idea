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

import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.messages.MessageBusConnection
import io.xmake.project.toolkit.ToolkitHostType.LOCAL
import io.xmake.project.toolkit.ToolkitHostType.SSH
import io.xmake.project.toolkit.ToolkitHostType.WSL
import io.xmake.utils.Logger
import io.xmake.utils.extension.ToolkitHostExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.EventListener
import java.util.EventObject

/** Application service coordinating toolkit persistence, scanning, registration, and change publication. */
@Service
@State(name = "toolkits", storages = [Storage("xmakeToolkits.xml", roamingType = RoamingType.DISABLED)])
class ToolkitManager(private val scope: CoroutineScope) : PersistentStateComponent<ToolkitManager.State> {

    /** Persisted XML shape of toolkit registrations. */
    class State {
        @XCollection(propertyElementName = "registeredToolKits")
        val registeredToolkits = mutableSetOf<Toolkit>()

        var defaultToolkitId: String? = null

        /** Compatibility inputs for options persisted by older plugin versions. */
        var lastSelectedToolkitId: String? = null

        var preferredToolkitId: String? = null
    }

    private val lock = Any()
    private val registry = ToolkitRegistry()
    private val scanner = ToolkitScanner()
    private val scanJobLock = Any()
    private val scanJobs = mutableMapOf<Project?, Job>()

    @Suppress("unused")
    private val projectCloseConnection: MessageBusConnection =
        ApplicationManager.getApplication().messageBus.connect().apply {
            subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
                override fun projectClosed(project: Project) {
                    synchronized(scanJobLock) {
                        scanJobs.remove(project)?.cancel()
                    }
                }
            })
        }

    internal var defaultToolkitId: String?
        get() = synchronized(lock) { registry.defaultToolkitId }
        set(value) {
            synchronized(lock) { registry.defaultToolkitId = value }
        }

    fun visibleToolkits(project: Project? = null): List<Toolkit> {
        val sshHostsById = currentSshHostsById(project)
        return synchronized(lock) { visibleToolkitsLocked(sshHostsById) }
    }

    fun registeredToolkits(project: Project? = null): List<Toolkit> {
        val sshHostsById = currentSshHostsById(project)
        return synchronized(lock) {
            val visibleById = visibleToolkitsLocked(sshHostsById).associateBy(Toolkit::id)
            registry.registeredToolkits.mapNotNull { registered -> visibleById[registered.id] }
        }
    }

    fun registeredToolkit(id: String, project: Project? = null): Toolkit? {
        val sshHostsById = currentSshHostsById(project)
        return synchronized(lock) {
            visibleToolkitsLocked(sshHostsById)
                .firstOrNull { toolkit -> toolkit.id == id }
                ?.takeIf { it.isRegistered }
        }
    }

    fun requestScan(project: Project?) {
        if (project?.isDisposed == true) return

        val job = synchronized(scanJobLock) {
            if (scanJobs[project]?.isActive == true) return@synchronized null
            scope.launch(start = CoroutineStart.LAZY) { runScan(project) }.also { scanJobs[project] = it }
        } ?: return

        job.invokeOnCompletion {
            synchronized(scanJobLock) {
                if (scanJobs[project] === job) scanJobs.remove(project)
            }
        }
        job.start()
    }

    @Deprecated("Use registeredToolkits(project) instead")
    fun getRegisteredToolkits(): List<Toolkit> = registeredToolkits(null)

    @Deprecated("Use registeredToolkit(id, project) instead")
    fun findRegisteredToolkitById(id: String): Toolkit? = registeredToolkit(id, null)

    @Deprecated("Use register(installationId, project) instead")
    fun registerToolkit(toolkit: Toolkit) {
        register(toolkit.id, null)
    }

    @Deprecated("Use unregister(installationId) instead")
    fun unregisterToolkit(toolkit: Toolkit) {
        unregister(toolkit.id)
    }

    @Deprecated("Use requestScan(project) instead")
    fun detectXMakeToolkits(project: Project?) {
        requestScan(project)
    }

    @Deprecated("Scans complete independently of the legacy lifecycle")
    fun cancelDetection() = Unit

    @Deprecated("Use requestScan(project) instead")
    fun validateXMakeToolkit() = requestScan(null)

    @Deprecated("Use requestScan(project) instead")
    fun validateToolkits() = requestScan(null)

    @Deprecated("Scans complete independently of the legacy lifecycle")
    fun cancelValidation() = Unit

    @Deprecated("Use ToolkitListener.TOPIC instead")
    val fetchedToolkitsSet = mutableSetOf<Toolkit>()

    @Deprecated("Use ToolkitListener.TOPIC instead")
    fun addToolkitDetectedListener(listener: ToolkitDetectedListener) {
        legacyToolkitDetectedListeners.add(listener)
    }

    interface ToolkitDetectedListener : EventListener {
        fun onToolkitDetected(e: ToolkitDetectEvent)
        fun onAllToolkitsDetected()
    }

    class ToolkitDetectEvent(source: Toolkit) : EventObject(source)

    private val legacyToolkitDetectedListeners = mutableListOf<ToolkitDetectedListener>()

    @Deprecated("Use defaultToolkitId instead")
    @get:JvmName("legacyState")
    val state: State
        get() = State().apply {
            registeredToolkits.addAll(registeredToolkits(null).map(Toolkit::toPersistedToolkit))
            lastSelectedToolkitId = defaultToolkitId
        }

    fun register(id: String, project: Project? = null): Toolkit? {
        val sshHostsById = currentSshHostsById(project)
        val registrationResult = synchronized(lock) {
            val scannedToolkit = scanner.find(id)
                ?.takeIf { toolkit -> visibleHost(toolkit.host, sshHostsById) != null }
            val change = scannedToolkit?.let(registry::refresh)
            val registered = when {
                change != null -> change.toolkit
                scannedToolkit != null -> registry.register(scannedToolkit)
                else -> registry.find(id)
            } ?: return@synchronized null
            ToolkitRegistry.RegistrationResult(
                toolkit = resolve(registered, sshHostsById),
                changed = change?.changed ?: (scannedToolkit != null),
            )
        } ?: return null
        if (registrationResult.toolkit.host.type == WSL && !registrationResult.toolkit.host.hasBackend) {
            restoreWslBackend(registrationResult.toolkit)
        }
        if (registrationResult.changed) {
            publishToolkitChanges(listOf(registrationResult.toolkit))
            Logger.i(
                TAG,
                "registered toolkit: ${registrationResult.toolkit.name}, ${registrationResult.toolkit.id}",
            )
        }
        return registrationResult.toolkit
    }

    fun unregister(id: String) {
        val unregistered = synchronized(lock) { registry.unregister(id) } ?: return
        publishToolkitChanges(listOf(unregistered))
    }

    override fun getState(): State = synchronized(lock) { registry.toState() }

    override fun loadState(state: State) {
        val migrated = migrateLegacyState(state)
        val registered = synchronized(lock) {
            scanner.clear()
            registry.replaceState(migrated)
        }
        if (registered.size != migrated.registeredToolkits.size) {
            Logger.w(
                TAG,
                "Discarded ${migrated.registeredToolkits.size - registered.size} duplicate " +
                        "toolkit registrations at the same location",
            )
        }
        registered.forEach(::restoreWslBackend)
    }

    /** Converts legacy in-memory names without rewriting persistent storage during component load. */
    private fun migrateLegacyState(state: State): State {
        val migratedToolkits = state.registeredToolkits.mapTo(mutableSetOf()) { toolkit ->
            val host = toolkit.host
            // Legacy local hosts persisted the OS name as their ID; local hosts have no backend.
            val backendId = if (host.type == LOCAL) null else host.migratedBackendId
            if (backendId == host.backendId) {
                toolkit
            } else {
                toolkit.copy(host = ToolkitHost(host.type, backendId))
            }
        }
        val migrated = State()
        migrated.registeredToolkits.addAll(migratedToolkits)
        migrated.defaultToolkitId = state.defaultToolkitId
            ?: state.lastSelectedToolkitId
                    ?: state.preferredToolkitId
        return migrated
    }

    private fun visibleToolkitsLocked(
        sshHostsById: Map<ToolkitHost.Id, ToolkitHost>,
    ): List<Toolkit> =
        buildMap {
            scanner.toolkits.forEach { scanned ->
                val host = visibleHost(scanned.host, sshHostsById) ?: return@forEach
                val registered = registry.findByLocation(scanned.location)
                put(
                    scanned.id,
                    scanned.copy(
                        host = host.toRuntimeHost(),
                        isRegistered = registered != null,
                    ),
                )
            }
            registry.registeredToolkits.forEach { registered ->
                val resolved = resolve(registered, sshHostsById)
                put(resolved.id, resolved)
            }
        }.values.toList()

    private fun resolve(
        toolkit: Toolkit,
        sshHostsById: Map<ToolkitHost.Id, ToolkitHost>,
    ): Toolkit {
        val host = visibleHost(toolkit.host, sshHostsById)
            ?: return toolkit.copy(
                isRegistered = true,
                isAvailable = false,
            )

        val scannedToolkit = scanner.findByLocation(toolkit.location)
        return scannedToolkit
            ?.let { scanned ->
                toolkit.copy(
                    host = host.toRuntimeHost(),
                    isRegistered = true,
                    isAvailable = scanned.isAvailable,
                )
            }
            ?: toolkit.copy(
                host = host.toRuntimeHost(),
                isRegistered = true,
                isAvailable = isPathAvailable(toolkit),
            )
    }

    private fun visibleHost(
        host: ToolkitHost,
        sshHostsById: Map<ToolkitHost.Id, ToolkitHost>,
    ): ToolkitHost? = when (host.type) {
        LOCAL -> host
        WSL -> host.takeIf(ToolkitHost::hasBackend)
        SSH -> sshHostsById[host.id]
    }

    private fun currentSshHostsById(project: Project?): Map<ToolkitHost.Id, ToolkitHost> =
        ToolkitHostExtension.forHostType(SSH)
            ?.getHosts(project)
            .orEmpty()
            .associateBy(ToolkitHost::id)

    private fun isPathAvailable(toolkit: Toolkit): Boolean =
        scanner.lastScannedExecutablePaths(toolkit.host.id)
            ?.let { executablePaths -> toolkit.path in executablePaths }
            ?: true

    private fun applyScanResult(
        result: ToolkitScanner.ScanResult,
        sshHostsById: Map<ToolkitHost.Id, ToolkitHost>,
    ): List<Toolkit> =
        synchronized(lock) {
            val normalizedToolkits = mutableListOf<Toolkit>()
            val changedRegistrations = mutableListOf<Toolkit>()
            result.toolkits.forEach { scannedToolkit ->
                val registration = registry.refresh(scannedToolkit)
                if (registration != null) {
                    normalizedToolkits += scannedToolkit.copy(id = registration.toolkit.id)
                    if (registration.changed) changedRegistrations += registration.toolkit
                } else {
                    normalizedToolkits += scannedToolkit
                }
            }

            val normalizedResult = result.copy(toolkits = normalizedToolkits)
            val affected = scanner.apply(normalizedResult) +
                    changedRegistrations +
                    registry.markUnavailablePaths(result.host.id, result.executablePaths)
            resolveChanges(affected, sshHostsById)
        }

    private fun applyHosts(
        hostsByType: Map<ToolkitHostType, List<ToolkitHost>>,
        sshHostsById: Map<ToolkitHost.Id, ToolkitHost>,
    ): List<Toolkit> =
        synchronized(lock) {
            // SSH hosts are visibility-filtered per project, so their scan results survive host removal.
            val applicationHostsByType = hostsByType.filterKeys { hostType ->
                hostType == LOCAL || hostType == WSL
            }
            val affectedToolkits = scanner.applyHosts(applicationHostsByType) +
                    registry.markUnavailableHosts(applicationHostsByType)
            resolveChanges(affectedToolkits, sshHostsById)
        }

    private fun resolveChanges(
        toolkits: List<Toolkit>,
        sshHostsById: Map<ToolkitHost.Id, ToolkitHost>,
    ): List<Toolkit> =
        toolkits
            .associateBy(Toolkit::id)
            .values
            .map { toolkit ->
                if (registry.findByLocation(toolkit.location) == null) {
                    toolkit.copy(isRegistered = false)
                } else {
                    resolve(toolkit, sshHostsById)
                }
            }

    private fun applyRestoredWslBackend(id: String, host: ToolkitHost): Toolkit? =
        synchronized(lock) {
            val toolkit = registry.find(id) ?: return@synchronized null
            if (toolkit.host.type != WSL || host.type != WSL) return@synchronized null
            scanner.lastScannedExecutablePaths(toolkit.host.id)?.let { paths ->
                if (toolkit.path !in paths) return@synchronized null
            }
            registry.updateHost(id, host)
        }

    private suspend fun runScan(project: Project?) {
        try {
            val hostsByType = scanner.getHostsByType(project)
            val sshHostsById = hostsByType[SSH].orEmpty().associateBy(ToolkitHost::id)
            scanner.scan(project, hostsByType.values.flatten()).collect { scanResult ->
                val affectedToolkits = applyScanResult(scanResult, sshHostsById)
                publishToolkitChanges(affectedToolkits)
                affectedToolkits.forEach { toolkit -> Logger.i(TAG, "toolkit changed: $toolkit") }
            }
            val affectedToolkits = applyHosts(hostsByType, sshHostsById)
            publishToolkitChanges(affectedToolkits)
            affectedToolkits.forEach { toolkit ->
                Logger.i(TAG, "toolkit host removed: ${toolkit.host.id.canonical}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Logger.e(TAG, "Failed to scan for XMake toolkits", error)
        }
    }

    private fun restoreWslBackend(toolkit: Toolkit) {
        if (toolkit.host.type != WSL) return

        scope.launch(Dispatchers.IO) {
            try {
                val host = resolveWslHost(toolkit.host)
                applyRestoredWslBackend(toolkit.id, host)?.let { restored ->
                    publishToolkitChanges(listOf(restored))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Logger.w(TAG, "Failed to restore toolkit ${toolkit.id}: ${error.message.orEmpty()}")
            }
        }
    }

    private fun resolveWslHost(host: ToolkitHost): ToolkitHost {
        val distribution = WslDistributionManager.getInstance().installedDistributions
            .find { distribution -> distribution.id == host.backendId }
            ?: return host
        return ToolkitHost.wsl(distribution)
    }

    private fun publishToolkitChanges(affectedToolkits: List<Toolkit>) {
        if (affectedToolkits.isEmpty()) return
        val application = ApplicationManager.getApplication() ?: return
        if (application.isDisposed) return
        application.messageBus.syncPublisher(ToolkitListener.TOPIC).toolkitsChanged()
        publishLegacyToolkitEvents(affectedToolkits)
    }

    private fun publishLegacyToolkitEvents(affectedToolkits: List<Toolkit>) {
        if (legacyToolkitDetectedListeners.isEmpty()) return
        affectedToolkits.forEach { toolkit ->
            fetchedToolkitsSet.removeIf { known -> known.id == toolkit.id }
            fetchedToolkitsSet.add(toolkit)
            legacyToolkitDetectedListeners.forEach { listener ->
                listener.onToolkitDetected(ToolkitDetectEvent(toolkit))
            }
        }
        legacyToolkitDetectedListeners.forEach { listener -> listener.onAllToolkitsDetected() }
    }

    companion object {
        private const val TAG = "ToolkitManager"

        fun getInstance(): ToolkitManager =
            serviceOrNull() ?: error("Failed to get ToolkitManager")
    }
}
