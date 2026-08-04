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
 * @file        TargetPathResolver.kt
 *
 */
package io.xmake.debug

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.utils.Logger
import io.xmake.utils.SystemUtils
import io.xmake.utils.execute.runProcess
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Resolves the built executable path for an xmake target by running
 * `xmake l scripts/targetpath.lua <target>` and parsing the absolute path printed between the
 * `__begin__` and `__end__` markers. Shared by [XMakeDebugSession] (to locate the debuggee) and by
 * [CustomBuildTargetsSupport] (to tell CLion the executable of each custom build target).
 */
object TargetPathResolver {

    private const val TAG = "TargetPathResolver"

    /**
     * Resolve the absolute executable path for [targetName] (`"default"`/blank picks xmake's
     * default binary target). Returns null when no toolkit is available, the script is missing, or
     * the target produces no binary. Runs the probe synchronously — call off the EDT.
     *
     * The xmake binary is taken from the activated toolkit, falling back to any registered toolkit,
     * so this works regardless of which run configuration is selected (the native "Xmake Executable"
     * config leaves `activatedToolkit` null). LOCAL toolkits only — CLion needs a local path.
     */
    fun resolve(project: Project, targetName: String): String? {
        Logger.v(TAG, "Getting executable path for target: $targetName")
        val toolkit = project.activatedToolkit
            ?: ToolkitManager.getInstance().getRegisteredToolkits().firstOrNull()
        if (toolkit != null && toolkit.isOnRemote) return null
        val xmakeBinary = toolkit?.path ?: "xmake"
        val targetPathScript = SystemUtils.getScriptPath("targetpath.lua")
        if (targetPathScript == null) {
            Logger.e(TAG, "targetpath.lua script not found")
            return null
        }

        val parameters = mutableListOf("l", targetPathScript)
        if (targetName != "default" && targetName.isNotEmpty()) {
            parameters.add(targetName)
        }

        val commandLine = GeneralCommandLine(xmakeBinary)
            .withParameters(parameters)
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(project.basePath)
            .withEnvironment("XMAKE_SKIP_HISTORY", "1")
            .withEnvironment("XMAKE_ROOT", "y")
            .withEnvironment("XMAKE_COLOR_TERM", "nocolor")

        return runBlocking {
            val process = commandLine.createProcess()
            val (result, _) = runProcess(process)
            val output = result.getOrNull()?.trim() ?: return@runBlocking null

            // parse output with tag __begin__ ... __end__
            val regex = "__begin__([\\s\\S]*?)__end__".toRegex()
            val matchResult = regex.find(output)
            val path = matchResult?.groupValues?.get(1)?.trim()

            if (path != null && !File(path).isAbsolute) {
                return@runBlocking File(project.basePath, path).absolutePath
            }
            path
        }
    }
}
