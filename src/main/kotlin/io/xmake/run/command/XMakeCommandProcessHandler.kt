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
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.xmake.shared.XMakeProblem
import io.xmake.utils.SystemUtils.parseProblem
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class XMakeCommandProcessHandler(
    private val project: Project,
    private val command: XMakeCommand,
    private val options: XMakeConsoleOptions = XMakeConsoleOptions(),
    private val onTextAvailable: (String, Key<*>) -> Unit = { _, _ -> },
    private val onProblems: (List<XMakeProblem>) -> Unit = {},
) {
    val processHandler: ProcessHandler = createProcessHandler()

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
                        continuation.resumeWithException(
                            ExecutionException(
                                "XMake command failed with exit code ${event.exitCode}: ${command.commandLine.commandLineString}",
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
            val handler = KillableColoredProcessHandler(process, command.commandLine.commandLineString, Charsets.UTF_8)

            handler.addProcessListener(object : ProcessListener {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
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

        return output.split(LINE_BREAK_REGEX)
            .mapNotNull { parseProblem(it.trim(), path) }
    }

    private companion object {
        val LINE_BREAK_REGEX = Regex("\\r\\n|\\n|\\r")
    }
}