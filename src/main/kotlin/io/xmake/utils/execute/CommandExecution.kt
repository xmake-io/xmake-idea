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
 * @file        CommandExecution.kt
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
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.utils.extension.ToolkitHostExtension
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private val Log = fileLogger()

private fun GeneralCommandLine.createLocalProcess(): Process =
    toProcessBuilder().start()

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
    return patchedCommandLine.toProcessBuilder().start()
}

fun GeneralCommandLine.createProcess(
    toolkit: Toolkit,
    project: Project? = null,
    remoteWorkingDirectory: String? = null,
): Process = createProcess(toolkit.host, project, remoteWorkingDirectory)

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
        throw ProcessTimeoutException("Process did not finish within $timeout", error)
    } finally {
        runCatching { destroyForcibly() }
    }
}

/** The decoded result of one process run. */
data class CommandResult(
    val commandLine: String,
    val exitCode: Int,
    val stdOut: String,
    val stdErr: String,
)

/** Starts [this] on [host], captures both streams, and waits up to [timeout] for it to exit. A
 *  WSL command needs a Linux path for [remoteWorkingDirectory]. */
suspend fun GeneralCommandLine.executeOn(
    host: ToolkitHost,
    project: Project? = null,
    timeout: Duration,
    remoteWorkingDirectory: String? = null,
): CommandResult {
    Log.debug("Running on ${host.id.canonical}: $commandLineString")
    val startedAt = System.nanoTime()
    // The outer timeout bounds the start phase (process creation, SSH connections); the inner
    // awaitExit timeout bounds the wait phase once the process is running.
    val result = withTimeoutOrNull(timeout) {
        runInterruptible(Dispatchers.IO) {
            createProcess(host, project, remoteWorkingDirectory)
        }.awaitExit(timeout)
    } ?: throw ProcessTimeoutException(
        "Process did not finish within $timeout: $commandLineString",
    )
    Log.debug(
        "Finished $commandLineString with exit ${result.exitCode} " +
                "after ${(System.nanoTime() - startedAt).nanoseconds}",
    )
    return CommandResult(
        commandLine = commandLineString,
        exitCode = result.exitCode,
        stdOut = result.stdOut.decodeProcessText(),
        stdErr = result.stdErr.decodeProcessText(),
    )
}

internal const val MAX_FAILURE_DETAIL_LENGTH = 4000

/** Quotes [text] for a failure message, keeping the tail - the diagnostics that name the
 *  failure sit at the end of command output - and marking a truncated quote with an ellipsis. */
internal fun failureDetail(text: String): String =
    if (text.length <= MAX_FAILURE_DETAIL_LENGTH) text
    else "…${text.takeLast(MAX_FAILURE_DETAIL_LENGTH)}"

/**
 * Starts the command line as a local process, captures both streams, and blocks until it exits
 * or [timeout] elapses. A timed-out process is killed, and the returned output carries
 * [ProcessOutput.isTimeout]. The call blocks, so run it off the EDT.
 */
fun GeneralCommandLine.runProcessBlocking(timeout: Duration): ProcessOutput {
    require(timeout > Duration.ZERO) { "Process timeout must be positive: $timeout" }
    return CapturingProcessHandler(this).runProcess(timeout.inWholeMilliseconds.toInt(), true)
}

private val nativeProcessCharset: Charset =
    System.getProperty("native.encoding")?.let(Charset::forName) ?: Charset.defaultCharset()

/** Decodes process output as UTF-8, falling back to the native charset for invalid bytes. */
internal fun ByteArray.decodeProcessText(): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(this))
        .toString()
} catch (_: CharacterCodingException) {
    String(this, nativeProcessCharset)
}
