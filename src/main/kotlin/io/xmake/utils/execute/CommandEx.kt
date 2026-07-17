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
import com.intellij.execution.process.*
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.execution.wsl.rootMappings
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.io.awaitExit
import io.xmake.project.console.XMakeConsole
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.shared.XMakeProblem
import io.xmake.utils.SystemUtils.parseProblem
import io.xmake.utils.extension.ToolkitHostExtension

private val Log = logger<GeneralCommandLine>()

private val EP_NAME: ExtensionPointName<ToolkitHostExtension> =
    ExtensionPointName("io.xmake.toolkitHostExtension")

fun GeneralCommandLine.createLocalProcess(): Process{
    return this
        .also { Log.info("commandOnLocal: ${this.commandLineString}") }
        .toProcessBuilder().start()
}

fun GeneralCommandLine.createWslProcess(wslDistribution: WSLDistribution, project: Project? = null): Process {
    val commandInWsl: GeneralCommandLine = wslDistribution.patchCommandLine(
        object : GeneralCommandLine(this) {
            init {
                parametersList.clearAll()
            }
        }, project,
        WSLCommandLineOptions().apply {
            wslDistribution.rootMappings
            isLaunchWithWslExe = true
//            remoteWorkingDirectory = workingDirectory?.toCanonicalPath()
        }
    ).apply {
        workDirectory?.let {
            withWorkDirectory(WslPath(wslDistribution.id, it.path).toWindowsUncPath())
        }
        parametersList.replaceOrAppend(this@createWslProcess.exePath, this@createWslProcess.commandLineString)
    }
    return commandInWsl
        .also { Log.info("commandInWsl: ${commandInWsl.commandLineString}") }
        .toProcessBuilder().start()
}

fun GeneralCommandLine.createProcess(toolkit: Toolkit): Process {
    return with(toolkit) {
        Log.info("createProcessWithToolkit: $toolkit")
        when (host.type) {
            LOCAL -> {
                this@createProcess.createLocalProcess()
            }

            WSL -> {
                val wslDistribution = host.target as WSLDistribution
                this@createProcess.createWslProcess(wslDistribution)
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

fun runProcessWithHandler(
    project: Project,
    console: XMakeConsole,
    command: GeneralCommandLine,
    showConsole: Boolean = true,
    showProblem: Boolean = false,
    showExitCode: Boolean = false,
    createProcess: (GeneralCommandLine) -> Process,
): ProcessHandler? {

    val process = try {
        createProcess(command)
    } catch (e: ProcessNotCreatedException) {
        return null
    }
    val processHandler = KillableColoredProcessHandler(process, command.commandLineString, Charsets.UTF_8)
    var content = ""

    processHandler.addProcessListener(object : ProcessListener {
        override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            super.onTextAvailable(event, outputType)
            console.print(event.text, ConsoleViewContentType.getConsoleViewType(outputType))
            content += event.text
        }
    })

    if (showConsole) {
        console.showOutput()
    }

    if (showProblem) {
        processHandler.addProcessListener(object : ProcessListener {
            override fun processTerminated(e: ProcessEvent) {
                val problems = mutableListOf<XMakeProblem>()
                content.split(Regex("\\r\\n|\\n|\\r")).forEach {
                    val problem = parseProblem(it.trim())
                    if (problem !== null) {
                        problems.add(problem)
                    }
                }
                console.updateProblems(problems)
            }
        })
    }

    if (showExitCode) {
        ProcessTerminatedListener.attach(processHandler)
    }

    processHandler.startNotify()
    ProcessTerminatedListener.attach(processHandler, project)
    return processHandler
}
