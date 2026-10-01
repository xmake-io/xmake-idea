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
 * @author      windchargerj
 * @file        CommandEx.kt
 *
 */
package io.xmake.utils.execute

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.ExecutionResult
import com.intellij.execution.processTools.getBareExecutionResult
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.utils.extension.ToolkitHostExtension
import java.io.File
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

private val Log = logger<GeneralCommandLine>()

fun GeneralCommandLine.createLocalProcess(): Process {
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
                val wslDistribution = host.requireWslDistribution()
                this@createProcess.createWslProcess(wslDistribution, project, workingDirectory)
            }

            SSH -> {
                ToolkitHostExtension.requireForHostType(SSH).startProcess(host, this@createProcess)
            }
        }
    }
}

/** Thrown when a subprocess does not exit within the allowed time and had to be killed. */
class ProcessTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Collects the process's output with a hard time bound. The process is killed on timeout or
 * caller cancellation; killing an already terminated process is a documented no-op, so one
 * cleanup path covers every outcome.
 */
suspend fun Process.awaitBounded(timeout: Duration): ExecutionResult = try {
    withTimeout(timeout) { getBareExecutionResult() }
} catch (error: TimeoutCancellationException) {
    throw ProcessTimeoutException("Process did not exit within $timeout", error)
} finally {
    runCatching { destroyForcibly() }
}
