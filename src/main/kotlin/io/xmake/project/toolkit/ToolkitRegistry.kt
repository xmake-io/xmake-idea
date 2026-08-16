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
 */
package io.xmake.project.toolkit

/** Toolkits registered by the user, mirroring the persisted state. */
internal class ToolkitRegistry {
    /** A registration committed by the registry, and whether it changed the registry. */
    internal data class RegistrationResult(
        val toolkit: Toolkit,
        val changed: Boolean,
    )

    private val entries = linkedMapOf<String, Toolkit>()
    var defaultToolkitId: String? = null

    val registeredToolkits: List<Toolkit>
        get() = entries.values.toList()

    fun find(id: String): Toolkit? = entries[id]

    fun findByLocation(location: Toolkit.Location): Toolkit? =
        entries.values.firstOrNull { registered -> registered.location == location }

    /** Registers a scanned toolkit, dropping the runtime backend from the stored metadata. */
    fun register(scannedToolkit: Toolkit): Toolkit {
        val registered = scannedToolkit.copy(
            host = scannedToolkit.host.toPersistedHost(),
            isRegistered = true,
            isAvailable = !scannedToolkit.requiresBackend,
        )
        entries[registered.id] = registered
        return registered
    }

    fun unregister(id: String): Toolkit? {
        val toolkit = entries.remove(id) ?: return null
        if (defaultToolkitId == toolkit.id) defaultToolkitId = null
        return toolkit.copy(isRegistered = false)
    }

    /** Refreshes a registration against a scanned toolkit, or null when the toolkit is unregistered. */
    fun refresh(scannedToolkit: Toolkit): RegistrationResult? {
        val registered = findByLocation(scannedToolkit.location) ?: return null
        val refreshed = scannedToolkit.copy(
            host = registered.host,
            id = registered.id,
            isRegistered = true,
            isAvailable = scannedToolkit.isAvailable,
        )
        if (registered == refreshed) return RegistrationResult(registered, changed = false)
        entries.remove(registered.id)
        entries[refreshed.id] = refreshed
        return RegistrationResult(refreshed, changed = true)
    }

    /** Marks registrations unavailable when a successful scan omits their executable paths. */
    fun markUnavailablePaths(hostId: ToolkitHost.Id, executablePaths: Set<String>): List<Toolkit> {
        return entries.values
            .filter { toolkit ->
                toolkit.host.id == hostId && toolkit.path !in executablePaths
            }
            .mapNotNull(::markUnavailable)
    }

    /** Marks registrations unavailable when current provider results omit their hosts. */
    fun markUnavailableHosts(
        hostsByType: Map<ToolkitHostType, List<ToolkitHost>>,
    ): List<Toolkit> {
        val hostIdsByType = hostsByType.mapValues { (_, hosts) ->
            hosts.mapTo(mutableSetOf(), ToolkitHost::id)
        }
        return entries.values
            .filter { toolkit ->
                toolkit.host.type in hostIdsByType &&
                        toolkit.host.id !in hostIdsByType.getValue(toolkit.host.type)
            }
            .mapNotNull(::markUnavailable)
    }

    fun updateHost(id: String, host: ToolkitHost): Toolkit? {
        val toolkit = entries[id] ?: return null
        if (toolkit.host.id != host.id) return null
        val isAvailable = host.hasBackend
        if (
            toolkit.host == host &&
            toolkit.host.backend === host.backend &&
            toolkit.isAvailable == isAvailable
        ) {
            return null
        }
        val updated = toolkit.copy(host = host.toRuntimeHost(), isAvailable = isAvailable)
        entries[updated.id] = updated
        return updated
    }

    fun toState(): ToolkitManager.State = ToolkitManager.State().apply {
        defaultToolkitId = this@ToolkitRegistry.defaultToolkitId
        entries.values.mapTo(registeredToolkits) { toolkit -> toolkit.toPersistedToolkit() }
    }

    fun replaceState(state: ToolkitManager.State): List<Toolkit> {
        entries.clear()
        var resolvedDefaultId = state.defaultToolkitId
        state.registeredToolkits.forEach { persisted ->
            val toolkit = persisted.copy(
                host = persisted.host.toPersistedHost(),
                isRegistered = true,
                isAvailable = !persisted.requiresBackend,
            )
            val existing = findByLocation(toolkit.location)
            if (existing != null) {
                if (persisted.id == state.defaultToolkitId) resolvedDefaultId = existing.id
                return@forEach
            }
            entries[toolkit.id] = toolkit
        }
        defaultToolkitId = resolvedDefaultId
        return entries.values.toList()
    }

    private fun markUnavailable(toolkit: Toolkit): Toolkit? {
        if (!toolkit.isAvailable && !toolkit.host.hasBackend) return null
        val unavailable = toolkit.copy(host = toolkit.host.toPersistedHost(), isAvailable = false)
        entries[unavailable.id] = unavailable
        return unavailable
    }
}
