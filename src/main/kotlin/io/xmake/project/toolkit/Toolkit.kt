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
 * @file        Toolkit.kt
 *
 */
package io.xmake.project.toolkit

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID

@Tag("toolkit")
data class Toolkit(
    @Attribute
    val name: String = "",
    @Property(surroundWithTag = false)
    val host: ToolkitHost = ToolkitHost(ToolkitHostType.LOCAL),
    @Attribute
    val path: String = "",
    @Attribute
    val version: String = "",
    @Attribute
    val id: String = createId(host, path),
) {
    @get:Transient
    var isRegistered: Boolean = false

    @get:Transient
    var isValid: Boolean = true

    @get:Transient
    val isOnRemote: Boolean
        get() = with(host) { type == ToolkitHostType.WSL || type == ToolkitHostType.SSH }

    /** Physical installation address: the host ID and the executable path. */
    internal val location: Location
        get() = Location(host.id, path)

    /** A toolkit installation's physical address, independent of its persisted id. */
    internal data class Location(
        val hostId: ToolkitHost.Id,
        val path: String,
    )

    companion object {
        internal fun createId(host: ToolkitHost, path: String): String {
            val seed = "xmake-toolkit-v2\u0000${host.id.canonical}\u0000$path"
            return UUID.nameUUIDFromBytes(seed.toByteArray(UTF_8)).toString()
        }
    }
}
