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
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.xmake.project.console.XMakeConsole
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.XMakeVersionRefusal
import io.xmake.project.toolkit.XMakeVersionRefusalException
import io.xmake.shared.XMakeProblem
import io.xmake.utils.execute.ProcessTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@Service(Service.Level.PROJECT)
/** The one xmake command runner of this project. Every public entry other than [submit],
 *  [submitAfter], and [runExclusive] assumes the caller is inside one of those three. */
internal class XMakeExecutor(
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
        timeout = BUILD_TIMEOUT,
        options = options,
        onTextAvailable = { text, outputType ->
            console.print(text, ConsoleViewContentType.getConsoleViewType(outputType))
        },
        beforeStart = { if (options.showConsole) console.showOutput() },
    )

    suspend fun executeSilently(command: XMakeCommand) {
        runAndAwait(command, timeout = ACTION_TIMEOUT)
    }

    /** Captures stdout of one command. A project script that refuses this xmake version
     *  (its set_xmakever asks for a newer one) throws [XMakeVersionRefusalException] instead of
     *  the raw failure, so callers can tell the two apart. */
    suspend fun captureStandardOutput(command: XMakeCommand): String {
        val output = StringBuilder()
        try {
            runAndAwait(
                command,
                timeout = QUERY_TIMEOUT,
                onTextAvailable = { text, outputType ->
                    if (ProcessOutputType.isStdout(outputType)) output.append(text)
                },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val match = (error as? XMakeCommandException)?.output?.let(MINIMUM_VERSION::find) ?: throw error
            val (_, minimumVersion) = match.destructured
            throw XMakeVersionRefusalException(XMakeVersionRefusal(command.toolkit, minimumVersion), error)
        }
        return output.toString()
    }

    /** Awaits one build command with the Build view's output callbacks. */
    suspend fun awaitBuild(
        command: XMakeCommand,
        options: XMakeConsoleOptions,
        onTextAvailable: (String, Key<*>) -> Unit,
        onProblems: (List<XMakeProblem>) -> Unit,
    ) = runAndAwait(
        command,
        timeout = BUILD_TIMEOUT,
        options = options,
        onTextAvailable = onTextAvailable,
        onProblems = onProblems,
    )

    /** Runs [command] to successful completion. */
    private suspend fun runAndAwait(
        command: XMakeCommand,
        timeout: Duration,
        options: XMakeConsoleOptions = XMakeConsoleOptions(),
        onTextAvailable: (String, Key<*>) -> Unit = { _, _ -> },
        onProblems: (List<XMakeProblem>) -> Unit = {},
        beforeStart: () -> Unit = {},
    ) {
        if (project.isDisposed) {
            throw ExecutionException("Project was disposed before XMake could start")
        }

        beforeStart()
        try {
            withTimeout(timeout) {
                XMakeCommandProcessHandler(project, command, options, onTextAvailable, onProblems)
                    .awaitSuccessfulCompletion()
            }
        } catch (error: TimeoutCancellationException) {
            throw ProcessTimeoutException(
                "XMake command did not finish within $timeout: ${command.commandLine.commandLineString}",
                error,
            )
        }
    }

    private companion object {
        val BUILD_TIMEOUT = 2.hours
        val ACTION_TIMEOUT = 10.minutes
        val QUERY_TIMEOUT = 5.minutes
    }
}

internal val Project.xmakeExecutor: XMakeExecutor
    get() = serviceOrNull() ?: error("Failed to get XMakeExecutor for $this")

// xmake's interpreter refuses the whole project script when its set_xmakever exceeds the
// running version; this text is identical on 2.8.7 and 3.x.
private val MINIMUM_VERSION = Regex("xmake v(\\S+) < v(\\S+), please run")
