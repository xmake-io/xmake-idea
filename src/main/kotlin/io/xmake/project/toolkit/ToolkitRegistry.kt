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

/** Toolkits registered by the user, mirroring the persisted state.
 *
 * Not thread safe on its own; every access must hold the ToolkitManager state lock,
 * which serializes registry changes against scans and persistence. */
internal class ToolkitRegistry {
    private val entries = linkedMapOf<String, Toolkit>()
    var defaultToolkitId: String? = null

    val toolkits: List<Toolkit>
        get() = entries.values.toList()

    fun find(id: String): Toolkit? = entries[id]

    fun register(toolkit: Toolkit): Toolkit {
        val registered = toolkit.toPersistedToolkit()
        entries[registered.id] = registered
        return registered
    }

    fun unregister(id: String): Toolkit? {
        val toolkit = entries.remove(id) ?: return null
        if (defaultToolkitId == toolkit.id) defaultToolkitId = null
        return toolkit
    }

    fun updateHost(id: String, host: ToolkitHost): Toolkit? {
        val toolkit = entries[id] ?: return null
        if (toolkit.host.type != host.type) return null
        val updated = toolkit.copy(host = host.toPersistedHost())
        entries[id] = updated
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
            val toolkit = persisted.toPersistedToolkit()
            val existing = entries.values.firstOrNull { registered ->
                registered.location == toolkit.location
            }
            if (existing != null) {
                if (persisted.id == state.defaultToolkitId) resolvedDefaultId = existing.id
                return@forEach
            }
            entries[toolkit.id] = toolkit
        }
        defaultToolkitId = resolvedDefaultId
        return entries.values.toList()
    }
}
