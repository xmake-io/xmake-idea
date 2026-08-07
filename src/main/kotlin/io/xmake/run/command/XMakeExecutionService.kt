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
import io.xmake.shared.XMakeProblem
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
        coroutineScope.async { taskMutex.withLock { task() } }

    suspend fun execute(
        console: XMakeConsole,
        command: XMakeCommand,
        options: XMakeConsoleOptions = XMakeConsoleOptions(),
    ) = start(
        command,
        options,
        onTextAvailable = { text, outputType ->
            console.print(text, ConsoleViewContentType.getConsoleViewType(outputType))
        },
        onProblems = console::updateProblems,
        beforeStart = { if (options.showConsole) console.showOutput() },
    )

    suspend fun captureStandardOutput(command: XMakeCommand): String {
        val output = StringBuilder()
        start(
            command,
            onTextAvailable = { text, outputType ->
                if (ProcessOutputType.isStdout(outputType)) output.append(text)
            },
        )
        return output.toString()
    }

    private suspend fun start(
        command: XMakeCommand,
        options: XMakeConsoleOptions = XMakeConsoleOptions(),
        onTextAvailable: (String, Key<*>) -> Unit = { _, _ -> },
        onProblems: (List<XMakeProblem>) -> Unit = {},
        beforeStart: () -> Unit = {},
    ) {
        if (project.isDisposed) {
            throw ExecutionException("Project was disposed before XMake could start")
        }

        beforeStart()
        XMakeCommandProcessHandler(project, command, options, onTextAvailable, onProblems)
            .awaitSuccessfulCompletion()
    }
}

internal val Project.xmakeExecutionService: XMakeExecutionService
    get() = getService(XMakeExecutionService::class.java)
        ?: error("Failed to get XMakeExecutionService for $this")
