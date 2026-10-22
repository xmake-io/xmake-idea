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

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.AnsiEscapeDecoder
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.KillableProcessHandler
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.xmake.shared.XMakeProblem
import io.xmake.utils.SystemUtils.parseProblem
import io.xmake.utils.execute.failureDetail
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Owns the OS process of one XMake command. Construction starts the process immediately
 * and may throw ExecutionException, so callers must consume the handler right away,
 * through [awaitSuccessfulCompletion] or the platform's `startProcess` lifecycle.
 */
internal class XMakeCommandProcessHandler(
    private val project: Project,
    private val command: XMakeCommand,
    private val options: XMakeConsoleOptions = XMakeConsoleOptions(),
    private val onTextAvailable: (String, Key<*>) -> Unit = { _, _ -> },
    private val onProblems: (List<XMakeProblem>) -> Unit = {},
) {
    private val stdErr = StringBuilder()

    val processHandler: ProcessHandler = createProcessHandler()

    private val remotePathMapper = RemotePathMapper.forToolkit(command.toolkit, command.workingDirectory, project)

    suspend fun awaitSuccessfulCompletion() {
        suspendCancellableCoroutine { continuation ->
            val completed = AtomicBoolean()
            val listener = object : ProcessListener {
                override fun processTerminated(event: ProcessEvent) {
                    processHandler.removeProcessListener(this)
                    if (!completed.compareAndSet(false, true)) return

                    if (event.exitCode == 0) {
                        continuation.resume(Unit)
                    } else {
                        val detail = stdErr.toString().trim()
                            .takeIf(String::isNotEmpty)
                            ?.let(::failureDetail)
                            ?.let { text -> ": $text" }
                            .orEmpty()
                        continuation.resumeWithException(
                            ExecutionException(
                                "XMake command failed with exit code ${event.exitCode}$detail. " +
                                        "Command: ${command.commandLine.commandLineString}",
                            ),
                        )
                    }
                }
            }
            processHandler.addProcessListener(listener)
            continuation.invokeOnCancellation {
                processHandler.removeProcessListener(listener)
                if (completed.compareAndSet(false, true)) terminateProcess()
            }

            try {
                processHandler.startNotify()
            } catch (error: Throwable) {
                processHandler.removeProcessListener(listener)
                terminateProcess()
                if (completed.compareAndSet(false, true)) continuation.resumeWithException(error)
            }
        }
    }

    private fun createProcessHandler(): ProcessHandler {
        val process = try {
            command.createProcess(project)
        } catch (error: Exception) {
            throw ExecutionException("Failed to start XMake command: ${command.commandLine.commandLineString}", error)
        }
        return try {
            val handler = if (options.preserveAnsiEscapes) {
                KillableProcessHandler(process, command.commandLine.commandLineString, Charsets.UTF_8)
            } else {
                KillableColoredProcessHandler(process, command.commandLine.commandLineString, Charsets.UTF_8)
            }

            handler.addProcessListener(object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    if (ProcessOutputType.isStderr(outputType)) stdErr.append(event.text)
                    onTextAvailable(event.text, outputType)
                }
            })

            if (options.showProblems) {
                val output = StringBuilder()
                handler.addProcessListener(object : ProcessListener {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        if (ProcessOutputType.isStdout(outputType) || ProcessOutputType.isStderr(outputType)) {
                            output.append(event.text)
                        }
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        onProblems(parseProblems(output))
                    }
                })
            }
            if (options.showExitCode) {
                ProcessTerminatedListener.attach(handler, project)
            }
            handler
        } catch (error: Throwable) {
            // The handler may not be fully constructed, so force-kill the started process.
            runCatching { process.destroyForcibly() }
                .exceptionOrNull()
                ?.let(error::addSuppressed)
            throw ExecutionException("Failed to prepare XMake command: ${command.commandLine.commandLineString}", error)
        }
    }

    private fun terminateProcess() {
        // Graceful destroy; the setup-failure path uses destroyForcibly instead.
        if (processHandler.isProcessTerminated || processHandler.isProcessTerminating) return
        runCatching { processHandler.destroyProcess() }
    }

    private fun parseProblems(output: CharSequence): List<XMakeProblem> {
        val path = try {
            Path.of(command.workingDirectory)
        } catch (_: InvalidPathException) {
            null
        }

        val plainOutput = StringBuilder()
        AnsiEscapeDecoder().escapeText(output.toString(), ProcessOutputTypes.STDOUT) { text, _ ->
            plainOutput.append(text)
        }
        return plainOutput.split(LINE_BREAK_REGEX)
            .mapNotNull { parseProblem(it.trim(), path) }
            .map(::withNavigablePath)
    }

    private fun withNavigablePath(problem: XMakeProblem): XMakeProblem {
        val remote = problem.resolvedPath ?: return problem
        val navigable = remotePathMapper.toLocal(remote) ?: return problem
        return problem.copy(navigablePath = navigable)
    }

    private companion object {
        val LINE_BREAK_REGEX = Regex("\\r\\n|\\n|\\r")
    }
}
