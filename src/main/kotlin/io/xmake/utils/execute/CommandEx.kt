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
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.processTools.ExecutionResult
import com.intellij.execution.processTools.getBareExecutionResult
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.utils.extension.ToolkitHostExtension
import java.io.File
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

private val Log = Logger.getInstance("io.xmake.utils.execute.CommandEx")

private fun GeneralCommandLine.createLocalProcess(): Process {
    return this
        .also { Log.info("commandOnLocal: ${this.commandLineString}") }
        .toProcessBuilder().start()
}

private fun GeneralCommandLine.createWslProcess(
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
    remoteWorkingDirectory: String? = null,
): Process {
    Log.info("createProcessWithToolkit: $toolkit")
    return createProcess(toolkit.host, project, remoteWorkingDirectory)
}

fun GeneralCommandLine.createProcess(
    host: ToolkitHost,
    project: Project? = null,
    remoteWorkingDirectory: String? = null,
): Process {
    return when (host.type) {
        LOCAL -> createLocalProcess()
        WSL -> createWslProcess(host.requireWslDistribution(), project, remoteWorkingDirectory)
        SSH -> ToolkitHostExtension.requireForHostType(SSH).startProcess(host, this)
    }
}

/** Thrown when a subprocess does not exit within the allowed time and had to be killed. */
class ProcessTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Waits for the process to exit and returns its captured output. A process that does not exit
 * within [timeout] is killed and [ProcessTimeoutException] is thrown; caller cancellation takes
 * the same cleanup path.
 */
suspend fun Process.awaitExit(timeout: Duration): ExecutionResult {
    if (timeout <= Duration.ZERO) {
        destroyForcibly()
        throw IllegalArgumentException("Process timeout must be positive: $timeout")
    }
    return try {
        withTimeout(timeout) { getBareExecutionResult() }
    } catch (error: TimeoutCancellationException) {
        throw ProcessTimeoutException("Process did not exit within $timeout", error)
    } finally {
        runCatching { destroyForcibly() }
    }
}

/**
 * Starts the command line as a local process, captures both streams, and blocks until it exits
 * or [timeout] elapses. A timed-out process is killed, and the returned output carries
 * [ProcessOutput.isTimeout]. The call blocks, so run it off the EDT.
 */
fun GeneralCommandLine.runProcessBlocking(timeout: Duration): ProcessOutput {
    require(timeout > Duration.ZERO) { "Process timeout must be positive: $timeout" }
    return CapturingProcessHandler(this).runProcess(timeout.inWholeMilliseconds.toInt(), true)
}
