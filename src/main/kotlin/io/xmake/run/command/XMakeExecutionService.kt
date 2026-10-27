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
import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.xmake.project.console.XMakeConsole
import io.xmake.project.toolkit.Toolkit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Service(Service.Level.PROJECT)
internal class XMakeExecutionService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val taskMutex = Mutex()

    /** Keeps the commands in one submitted operation from interleaving with another operation. */
    fun <T> submit(task: suspend () -> T): Deferred<T> =
        coroutineScope.async { runExclusive(task) }

    /**
     * Runs [before] first (outside the exclusive lock — typically another mutex-guarded
     * operation, e.g. a [com.intellij.task.ProjectTaskManager] build, that must fully finish
     * before [task] starts), then runs [task] without interleaving it with another XMake
     * operation. Never call this with a [before] that itself calls [submit]/[runExclusive]
     * concurrently with the returned [Deferred] still pending — the mutex is not reentrant.
     */
    fun <T> submitAfter(before: suspend () -> Unit, task: suspend () -> T): Deferred<T> =
        coroutineScope.async {
            before()
            runExclusive(task)
        }

    /** Runs a command operation without interleaving it with another XMake operation. */
    suspend fun <T> runExclusive(task: suspend () -> T): T = taskMutex.withLock { task() }

    suspend fun execute(
        console: XMakeConsole,
        command: XMakeCommand,
        options: XMakeConsoleOptions = XMakeConsoleOptions(),
    ) = runAndAwait(
        command,
        options,
        onTextAvailable = { text, outputType ->
            console.print(text, ConsoleViewContentType.getConsoleViewType(outputType))
        },
        beforeStart = { if (options.showConsole) console.showOutput() },
    )

    suspend fun executeSilently(command: XMakeCommand) {
        runAndAwait(command)
    }

    /** Captures stdout of one command. A project script that refuses this xmake version
     *  (its set_xmakever asks for a newer one) throws [XMakeProjectVersionError] instead of
     *  the raw failure, so callers can tell the two apart. */
    suspend fun captureStandardOutput(command: XMakeCommand): String {
        val output = StringBuilder()
        try {
            runAndAwait(
                command,
                onTextAvailable = { text, outputType ->
                    if (ProcessOutputType.isStdout(outputType)) output.append(text)
                },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw error.message?.takeIf(VERSION_REQUIREMENT::containsMatchIn)
                ?.let(::XMakeProjectVersionError) ?: error
        }
        return output.toString()
    }

    /** Runs [command] to successful completion. The caller holds the execution mutex. */
    private suspend fun runAndAwait(
        command: XMakeCommand,
        options: XMakeConsoleOptions = XMakeConsoleOptions(),
        onTextAvailable: (String, Key<*>) -> Unit = { _, _ -> },
        beforeStart: () -> Unit = {},
    ) {
        if (project.isDisposed) {
            throw ExecutionException("Project was disposed before XMake could start")
        }

        beforeStart()
        XMakeCommandProcessHandler(project, command, options, onTextAvailable)
            .awaitSuccessfulCompletion()
    }
}

internal val Project.xmakeExecutionService: XMakeExecutionService
    get() = getService(XMakeExecutionService::class.java)
        ?: error("Failed to get XMakeExecutionService for $this")

/** The project script refused this xmake version; the message is the failed command's
 *  report and carries xmake's own refusal text. */
internal class XMakeProjectVersionError(message: String) : RuntimeException(message)

/** The one wording of a version refusal, for logs and UI alike: the toolkit the project
 *  refused, and the failure xmake itself reported. */
internal fun XMakeProjectVersionError.userMessage(toolkit: Toolkit): String =
    "The project refused xmake ${toolkit.version} on ${toolkit.name}: $message"

// xmake's interpreter refuses the whole project script when its set_xmakever exceeds the
// running version; this text is identical on 2.8.7 and 3.x.
private val VERSION_REQUIREMENT = Regex("xmake v\\S+ < v\\S+, please run")
