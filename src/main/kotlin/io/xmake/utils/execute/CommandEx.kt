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
 * @file        CommandEx.kt
 *
 */
package io.xmake.utils.execute

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.io.awaitExit
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.utils.extension.ToolkitHostExtension
import java.io.File

private val Log = logger<GeneralCommandLine>()

private val EP_NAME: ExtensionPointName<ToolkitHostExtension> =
    ExtensionPointName("io.xmake.toolkitHostExtension")

fun GeneralCommandLine.createLocalProcess(): Process{
    return this
        .also { Log.info("commandOnLocal: ${this.commandLineString}") }
        .toProcessBuilder().start()
}

fun GeneralCommandLine.createWslProcess(
    wslDistribution: WSLDistribution,
    project: Project? = null,
    remoteWorkingDirectory: String? = null,
): Process {
    val commandInWsl = object : GeneralCommandLine(this) {}.apply {
        // The Windows-side wsl.exe process must not inherit a Linux working directory.
        setWorkDirectory(null as File?)
    }
    val options = WSLCommandLineOptions().apply {
        isLaunchWithWslExe = true
        remoteWorkingDirectory?.let { directory ->
            require(directory.startsWith('/')) { "WSL working directory must be an absolute Linux path: $directory" }
            this.remoteWorkingDirectory = directory
        }
    }
    val patchedCommandLine = wslDistribution.patchCommandLine(commandInWsl, project, options)

    return patchedCommandLine
        .also { Log.info("commandInWsl: ${patchedCommandLine.commandLineString}") }
        .toProcessBuilder().start()
}

fun GeneralCommandLine.createProcess(
    toolkit: Toolkit,
    project: Project? = null,
    workingDirectory: String? = null,
): Process {
    return with(toolkit) {
        Log.info("createProcessWithToolkit: $toolkit")
        when (host.type) {
            LOCAL -> {
                this@createProcess.createLocalProcess()
            }

            WSL -> {
                val wslDistribution = host.target as WSLDistribution
                this@createProcess.createWslProcess(wslDistribution, project, workingDirectory)
            }

            SSH -> {
                with(EP_NAME.extensions.first { it.KEY == "SSH" }) {
                    createProcess(toolkit.host)
                }
            }
        }
    }
}

suspend fun runProcess(process: Process): Pair<Result<String>, Int>{
    val result = process.getResultStdoutStr()
    val exitCode = process.awaitExit()
    return Pair(result, exitCode)
}
