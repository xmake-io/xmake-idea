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
package io.xmake.build

import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.FilePosition
import com.intellij.build.events.MessageEvent
import com.intellij.build.progress.BuildProgress
import com.intellij.build.progress.BuildProgressDescriptor
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import io.xmake.run.command.XMakeCommandProcessHandler
import io.xmake.run.command.XMakeConsoleOptions
import io.xmake.run.command.xmakeExecutionService
import io.xmake.shared.XMakeProblem
import kotlinx.coroutines.CancellationException
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromiseWithoutLogError
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("UnstableApiUsage")
class XMakeProjectTaskRunner : ProjectTaskRunner() {
    override fun canRun(
        project: Project,
        projectTask: ProjectTask,
        context: ProjectTaskContext?,
    ): Boolean = projectTask is XMakeBuildTask

    override fun run(
        project: Project,
        context: ProjectTaskContext,
        vararg tasks: ProjectTask,
    ): Promise<Result> {
        val buildTasks = tasks.map { task ->
            require(task is XMakeBuildTask) { "Unsupported XMake project task: ${task.javaClass.name}" }
            task
        }
        require(buildTasks.isNotEmpty()) { "No XMake project tasks were provided" }

        val title = buildTasks.singleOrNull()?.getPresentableName() ?: "XMake Build"
        val processHandler = XMakeBuildProcessHandler(title)
        val progress = createBuildProgress(project, context, buildTasks, processHandler, title)
        val completed = AtomicBoolean()
        val operation = project.xmakeExecutionService.submit {
            execute(project, buildTasks, progress, processHandler, completed, title)
        }
        val promise = operation.toPromiseWithoutLogError()
        processHandler.bind(operation)
        promise.onError { error ->
            if (error is CancellationException) {
                cancel(progress, processHandler, completed, title)
            } else {
                fail(progress, processHandler, completed, error, title)
            }
        }
        return promise
    }

    private suspend fun execute(
        project: Project,
        tasks: List<XMakeBuildTask>,
        progress: BuildProgress<BuildProgressDescriptor>,
        processHandler: XMakeBuildProcessHandler,
        completed: AtomicBoolean,
        title: String,
    ): Result = try {
        for (task in tasks) {
            for (command in task.commands) {
                XMakeCommandProcessHandler(
                    project,
                    command,
                    XMakeConsoleOptions(showProblems = true, preserveAnsiEscapes = true),
                    onTextAvailable = { text, outputType ->
                        progress.output(text, ProcessOutputType.fromKey(outputType))
                    },
                    onProblems = { problems ->
                        progress.reportProblems(problems, command.toolkit.requiresBackend)
                    },
                ).awaitSuccessfulCompletion()
            }
        }

        complete(processHandler, completed, SUCCESS_EXIT_CODE) {
            progress.finish()
        }
        result()
    } catch (error: CancellationException) {
        cancel(progress, processHandler, completed, title)
        throw error
    } catch (error: ProcessCanceledException) {
        cancel(progress, processHandler, completed, title)
        throw error
    } catch (error: Exception) {
        fail(progress, processHandler, completed, error, title)
        result(hasErrors = true)
    }

    private fun cancel(
        progress: BuildProgress<BuildProgressDescriptor>,
        processHandler: XMakeBuildProcessHandler,
        completed: AtomicBoolean,
        title: String,
    ) {
        complete(processHandler, completed, CANCELED_EXIT_CODE) {
            progress.cancel(System.currentTimeMillis(), "$title was canceled")
        }
    }

    private fun fail(
        progress: BuildProgress<BuildProgressDescriptor>,
        processHandler: XMakeBuildProcessHandler,
        completed: AtomicBoolean,
        error: Throwable,
        title: String,
    ) {
        val message = error.message ?: "$title failed"
        complete(processHandler, completed, FAILED_EXIT_CODE) {
            progress.output("$message\n", ProcessOutputType.STDERR)
            progress.fail(System.currentTimeMillis(), message)
        }
    }

    private inline fun complete(
        processHandler: XMakeBuildProcessHandler,
        completed: AtomicBoolean,
        exitCode: Int,
        completion: () -> Unit,
    ) {
        if (!completed.compareAndSet(false, true)) return
        try {
            completion()
        } finally {
            processHandler.finish(exitCode)
        }
    }

    private fun createBuildProgress(
        project: Project,
        context: ProjectTaskContext,
        tasks: List<XMakeBuildTask>,
        processHandler: XMakeBuildProcessHandler,
        title: String,
    ): BuildProgress<BuildProgressDescriptor> {
        val descriptor = DefaultBuildDescriptor(
            context.sessionId ?: UUID.randomUUID(),
            title,
            tasks.first().workingDirectory,
            System.currentTimeMillis(),
        ).apply {
            isActivateToolWindowWhenAdded = context.runConfiguration == null
            withProcessHandler(processHandler) { }
        }
        val progressDescriptor = object : BuildProgressDescriptor {
            override fun getTitle(): String = descriptor.title
            override fun getBuildDescriptor() = descriptor
        }
        return BuildViewManager.createBuildProgress(project).apply {
            start(progressDescriptor)
            processHandler.startNotify()
        }
    }

    private fun BuildProgress<BuildProgressDescriptor>.reportProblems(
        problems: List<XMakeProblem>,
        remote: Boolean,
    ) {
        for (problem in problems) {
            val kind = problem.messageKind()
            val message = problem.message.orEmpty()
            val position = problem.filePosition(remote)
            if (position == null) {
                message("XMake", message, kind, null)
            } else {
                fileMessage(position.path?.fileName?.toString() ?: "XMake", message, kind, position)
            }
        }
    }

    private fun XMakeProblem.filePosition(remote: Boolean): FilePosition? {
        if (remote) return null
        val resolvedPath = resolvedFilePath ?: return null
        val sourceLine = line?.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
        val sourceColumn = column?.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
        return FilePosition(resolvedPath.normalize(), sourceLine, sourceColumn)
    }

    private fun XMakeProblem.messageKind(): MessageEvent.Kind = when {
        kind?.contains("warning", ignoreCase = true) == true -> MessageEvent.Kind.WARNING
        kind?.contains("note", ignoreCase = true) == true -> MessageEvent.Kind.INFO
        else -> MessageEvent.Kind.ERROR
    }

    private fun result(hasErrors: Boolean = false): Result = object : Result {
        override fun isAborted(): Boolean = false
        override fun hasErrors(): Boolean = hasErrors
    }

    private companion object {
        const val SUCCESS_EXIT_CODE = 0
        const val FAILED_EXIT_CODE = 1
        const val CANCELED_EXIT_CODE = 130
    }
}
