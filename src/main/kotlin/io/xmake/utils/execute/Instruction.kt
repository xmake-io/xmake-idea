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
 * @file        Instruction.kt
 *
 */
package io.xmake.utils.execute

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.containers.map2Array

val predefinedPath = mapOf(
    "windows" to arrayOf(),
    "unix" to arrayOf(
        // Todo: Add more paths
        "\${HOME}/.local/bin",
        "/usr/local/bin",
        "/usr/bin",
        "/opt/homebrew/bin"
    )
)

val probeXmakeLocCommandOnWin = GeneralCommandLine("where.exe")
    .withParameters("xmake")
    .withCharset(Charsets.UTF_8)

val probeXmakeLocCommand = GeneralCommandLine("which")
    .withParameters(*arrayOf("xmake")
        .plus(predefinedPath["unix"]?.map2Array { "$it/xmake" } ?: emptyArray())
    )
    .withCharset(Charsets.UTF_8)

val probeXmakeVersionCommand
    get() = GeneralCommandLine()
        .withParameters("--version")
        .withCharset(Charsets.UTF_8)
