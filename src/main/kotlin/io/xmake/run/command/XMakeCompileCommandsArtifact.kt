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
import com.intellij.util.system.OS
import io.xmake.project.XMakeSettings
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.run.target.activeOrSingleXMakeBuildProfile
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val COMPILE_COMMANDS_FILE_NAME = "compile_commands.json"

data class XMakeCompileCommandsArtifact(
    val directory: String,
    val driver: String,
    val hostType: ToolkitHostType?,
    val wslDistributionName: String?,
)

fun compileCommandsArtifact(project: Project): XMakeCompileCommandsArtifact? {
    val file = compileCommandsArtifactFile(project)?.takeIf { Files.isRegularFile(it) } ?: return null
    val entry = runCatching {
        Json.parseToJsonElement(Files.readString(file)).jsonArray.firstOrNull()?.jsonObject
    }.getOrNull() ?: return null
    val directory = entry["directory"]?.jsonPrimitive?.content ?: return null
    val driver = entry["arguments"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content ?: return null
    val host = project.activeOrSingleXMakeBuildProfile?.resolveToolkit(project)?.host
    return XMakeCompileCommandsArtifact(
        directory = directory,
        driver = driver,
        hostType = host?.type,
        wslDistributionName = host?.takeIf { it.type == ToolkitHostType.WSL }?.wslDistribution?.msId,
    )
}

/** Which toolchain kind resolves the artifact's implicit headers. */
enum class XMakeCompileCommandsDomain {
    SYSTEM,
    WSL,
    SSH,
    MSVC,
    MINGW,
}

fun XMakeCompileCommandsArtifact.domain(): XMakeCompileCommandsDomain? =
    when (hostType) {
        ToolkitHostType.SSH -> XMakeCompileCommandsDomain.SSH
        ToolkitHostType.WSL -> XMakeCompileCommandsDomain.WSL
        ToolkitHostType.LOCAL ->
            if (OS.CURRENT == OS.Windows) windowsLocalDomain() else XMakeCompileCommandsDomain.SYSTEM
        null -> null
    }

private fun XMakeCompileCommandsArtifact.windowsLocalDomain(): XMakeCompileCommandsDomain {
    val driverName = driver.substringAfterLast('/').substringAfterLast('\\').lowercase()
    return when (driverName) {
        "cl.exe", "clang-cl.exe" -> XMakeCompileCommandsDomain.MSVC
        else -> XMakeCompileCommandsDomain.MINGW
    }
}

fun compileCommandsArtifactFile(project: Project): Path? = runCatching {
    Path.of(
        project.xmakeProjectDirectories.resolveLocalSyncDirectory(),
        compileCommandsGeneratedPath(XMakeSettings.getInstance(project).state.compileCommandsPath),
    )
}.getOrNull()
