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

package io.xmake.run.command

import com.intellij.openapi.project.Project
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.util.system.OS
import io.xmake.project.XMakeSettings
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.run.target.activeOrSingleXMakeBuildProfile
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val COMPILE_COMMANDS_FILE_NAME = "compile_commands.json"

data class XMakeCompileCommandsContext(
    val compilerPath: String,
    val workingDirectory: String?,
    val host: ToolkitHost?,
)

val XMakeCompileCommandsContext.wslDistributionName: String?
    get() = host?.takeIf { it.type == ToolkitHostType.WSL }?.wslDistribution?.msId

val XMakeCompileCommandsContext.sshConfig: SshConfig?
    get() = host?.takeIf { it.type == ToolkitHostType.SSH }?.sshConfig

private fun firstCommandEntry(project: Project): JsonObject? {
    val file = compileCommandsFile(project)?.takeIf { Files.isRegularFile(it) } ?: return null
    return runCatching {
        Json.parseToJsonElement(Files.readString(file)).jsonArray.firstOrNull()?.jsonObject
    }.getOrNull()
}

fun compileCommandsContext(project: Project): XMakeCompileCommandsContext? {
    val entry = firstCommandEntry(project) ?: return null
    val compilerPath = runCatching {
        entry["arguments"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
    }.getOrNull() ?: return null
    val workingDirectory = runCatching {
        entry["directory"]?.jsonPrimitive?.content
    }.getOrNull()
    return XMakeCompileCommandsContext(
        compilerPath = compilerPath,
        workingDirectory = workingDirectory,
        host = project.activeOrSingleXMakeBuildProfile?.resolveToolkit(project)?.host,
    )
}

enum class XMakeCompileCommandsToolchainRequirement {
    SYSTEM,
    WSL,
    SSH,
    MSVC,
    MINGW,
}

@Suppress("OPT_IN_USAGE")
fun XMakeCompileCommandsContext.toolchainRequirement(): XMakeCompileCommandsToolchainRequirement? =
    when (host?.type) {
        ToolkitHostType.SSH -> XMakeCompileCommandsToolchainRequirement.SSH
        ToolkitHostType.WSL -> XMakeCompileCommandsToolchainRequirement.WSL
        ToolkitHostType.LOCAL ->
            if (OS.CURRENT == OS.Windows) windowsLocalRequirement() else XMakeCompileCommandsToolchainRequirement.SYSTEM
        null -> null
    }

private fun XMakeCompileCommandsContext.windowsLocalRequirement(): XMakeCompileCommandsToolchainRequirement {
    val compilerName = compilerPath.substringAfterLast('/').substringAfterLast('\\').lowercase()
    return when (compilerName) {
        "cl.exe", "clang-cl.exe" -> XMakeCompileCommandsToolchainRequirement.MSVC
        else -> XMakeCompileCommandsToolchainRequirement.MINGW
    }
}

fun compileCommandsFile(project: Project): Path? = runCatching {
    Path.of(
        project.xmakeProjectDirectories.resolveLocalSyncDirectory(),
        compileCommandsGeneratedPath(XMakeSettings.getInstance(project).state.compileCommandsPath),
    )
}.getOrNull()
