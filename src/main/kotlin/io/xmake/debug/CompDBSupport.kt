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
 * @file        CompDBSupport.kt
 *
 */
package io.xmake.debug

import com.intellij.openapi.project.Project
import io.xmake.project.xmakeSettings
import java.io.File

/**
 * Bridges xmake's generated `compile_commands.json` to CLion IntelliSense. On non-CLion IDEs the
 * underlying [DebugModuleLoader.attachCompileCommands] call is a no-op, so this is safe to invoke
 * unconditionally after the compile database is (re)generated.
 */
object CompDBSupport {

    /**
     * Resolve the local `compile_commands.json` from the configured output path
     * ([io.xmake.project.XMakeSettings]) relative to the project root. Mirrors where
     * `xmake project -k compile_commands <path>` writes the file.
     */
    fun compileCommandsFile(project: Project): File? {
        val base = project.basePath ?: return null
        val configured = project.xmakeSettings.state.compileCommandsPath.trim()
        return when {
            configured.isEmpty() -> File(base, "compile_commands.json")
            configured.endsWith(".json") -> File(base).resolve(configured)
            else -> File(base).resolve(configured).resolve("compile_commands.json")
        }
    }

    /** Refresh CLion IntelliSense from the generated compile database, if present. */
    fun refreshIntelliSense(project: Project) {
        val file = compileCommandsFile(project) ?: return
        if (!file.isFile) return
        DebugModuleLoader.attachCompileCommands(project, file.absolutePath)
    }
}
