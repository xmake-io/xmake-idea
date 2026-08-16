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
 * @file        ToolkitHost.kt
 *
 */
package io.xmake.project.toolkit

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import io.xmake.project.toolkit.ToolkitHostType.*

@Tag("toolkitHost")
data class ToolkitHost(
    @Attribute
    val type: ToolkitHostType = LOCAL,
    @Attribute("backendId")
    val backendId: String? = null,
) {
    /** The resolved platform object behind a persisted toolkit host. */
    internal sealed interface Backend {
        data class WSL(
            val distribution: WSLDistribution,
        ) : Backend

        data class SSH(
            val config: SshConfig,
        ) : Backend
    }

    /** Compatibility inputs for toolkit hosts persisted by versions before `backendId`. */
    @Attribute("id")
    var legacyId: String? = null

    @Attribute("targetId")
    var legacyTargetId: String? = null

    @Attribute("runtimeId")
    var legacyRuntimeId: String? = null

    @Transient
    internal var backend: Backend? = null

    internal val wslDistribution: WSLDistribution?
        get() = (backend as? Backend.WSL)?.distribution

    internal val sshConfig: SshConfig?
        get() = (backend as? Backend.SSH)?.config

    /** Presentable name derived from the resolved backend. */
    internal val displayName: String
        get() = when (type) {
            LOCAL -> LOCAL_DISPLAY_NAME
            WSL -> wslDistribution?.presentableName ?: type.name
            SSH -> sshConfig?.presentableShortName ?: type.name
        }

    internal val hasBackend: Boolean
        get() = backend != null

    internal fun requireWslDistribution(): WSLDistribution =
        wslDistribution ?: error("XMake WSL host backend is not available")

    /** Stable host ID across backend reloads. */
    internal val id: Id = Id(type, backendId)

    internal val migratedBackendId: String?
        get() = backendId ?: legacyId ?: legacyTargetId ?: legacyRuntimeId

    /** The persistable host form without the resolved runtime backend. */
    internal fun toPersistedHost(): ToolkitHost = ToolkitHost(type, backendId)

    /** A normalized runtime copy carrying the resolved backend. */
    internal fun toRuntimeHost(): ToolkitHost = toPersistedHost().apply {
        backend = this@ToolkitHost.backend
    }

    override fun toString(): String = "ToolkitHost(type=$type, backendId=$backendId)"

    /** Stable identity of a host: the host type and its backend ID. */
    internal data class Id(
        val type: ToolkitHostType,
        val backendId: String?,
    ) : Comparable<Id> {
        /** Canonical string form used for installation IDs and logs. */
        val canonical: String
            get() = listOfNotNull(type.name, backendId).joinToString(":")

        override fun compareTo(other: Id): Int =
            compareValuesBy(this, other, Id::type, Id::backendId)

        override fun toString(): String = canonical
    }

    companion object {
        private val LOCAL_DISPLAY_NAME = System.getProperty("os.name").orEmpty().ifBlank { "Local" }

        internal fun wsl(distribution: WSLDistribution): ToolkitHost =
            ToolkitHost(type = WSL, backendId = distribution.id).apply {
                backend = Backend.WSL(distribution)
            }

        internal fun ssh(config: SshConfig): ToolkitHost =
            ToolkitHost(type = SSH, backendId = config.id).apply {
                backend = Backend.SSH(config)
            }
    }
}
