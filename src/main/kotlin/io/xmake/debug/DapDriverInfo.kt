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
 * @file        DapDriverInfo.kt
 *
 */
package io.xmake.debug

import com.intellij.openapi.util.Version

/** The lowest GDB release that speaks DAP. */
internal val MIN_DAP_GDB_VERSION = Version(14, 1, 0)

enum class DapDriverType(val displayName: String) {
    LLDB("LLDB DAP"),
    GDB("GDB DAP"),
}

sealed interface DapSupport {
    data object Supported : DapSupport
    data class Unsupported(val version: Version) : DapSupport
    data class Unverified(val diagnostics: String) : DapSupport
}

data class DapDriverInfo(
    val path: String,
    val type: DapDriverType,
    val support: DapSupport,
)
