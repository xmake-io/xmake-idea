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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import io.xmake.project.console.FileReferenceFilter
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.run.command.XMakeConsoleOptions
import io.xmake.run.command.xmakeExecutor
import io.xmake.shared.XMakeProblem
import io.xmake.utils.execute.fetchGeneratedFile
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
        val session = BuildSession(progress, processHandler, title)
        val operation = project.xmakeExecutor.submit {
            execute(project, buildTasks, session)
        }
        val promise = operation.toPromiseWithoutLogError()
        processHandler.bind(operation)
        context.putUserData(BUILD_STOP_HANDLE, processHandler::destroyProcess)
        promise.onError { error ->
            if (error is CancellationException) {
                session.cancel()
            } else {
                session.fail(error)
            }
        }
        return promise
    }

    private suspend fun execute(
        project: Project,
        tasks: List<XMakeBuildTask>,
        session: BuildSession,
    ): Result {
        val progress = session.progress
        return try {
            for (task in tasks) {
                for (command in task.commands) {
                    project.xmakeExecutor.awaitBuild(
                        command,
                        XMakeConsoleOptions(showProblems = true, preserveAnsiEscapes = true),
                        onTextAvailable = { text, outputType ->
                            progress.output(text, ProcessOutputType.fromKey(outputType))
                        },
                        onProblems = { problems ->
                            progress.reportProblems(problems)
                        },
                    )
                }
                task.fetchOnSuccess?.let { generatedPath ->
                    val lastCommand = task.commands.last()
                    try {
                        fetchGeneratedFile(
                            project, lastCommand.toolkit, lastCommand.workingDirectory,
                            project.xmakeProjectDirectories.resolveLocalSyncDirectory(), generatedPath)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        progress.output(
                            "Failed to fetch $generatedPath: ${error.message ?: error.toString()}\n",
                            ProcessOutputType.STDERR,
                        )
                    }
                }
            }

            session.succeed()
            result()
        } catch (error: CancellationException) {
            // ProcessCanceledException is a CancellationException on this platform.
            session.cancel()
            throw error
        } catch (error: Exception) {
            session.fail(error)
            result(hasErrors = true)
        }
    }

    private class BuildSession(
        val progress: BuildProgress<BuildProgressDescriptor>,
        private val processHandler: XMakeBuildProcessHandler,
        private val title: String,
    ) {
        private val completed = AtomicBoolean()

        fun succeed() = finish(SUCCESS_EXIT_CODE) {
            progress.finish()
        }

        fun cancel() = finish(CANCELED_EXIT_CODE) {
            progress.cancel(System.currentTimeMillis(), "$title was canceled")
        }

        fun fail(error: Throwable) {
            val message = error.message ?: "$title failed"
            finish(FAILED_EXIT_CODE) {
                progress.output("$message\n", ProcessOutputType.STDERR)
                progress.fail(System.currentTimeMillis(), message)
            }
        }

        private inline fun finish(exitCode: Int, completion: () -> Unit) {
            if (!completed.compareAndSet(false, true)) return
            try {
                completion()
            } finally {
                processHandler.finish(exitCode)
            }
        }

        companion object {
            const val SUCCESS_EXIT_CODE = 0
            const val FAILED_EXIT_CODE = 1
            const val CANCELED_EXIT_CODE = 130
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
            withExecutionFilter(FileReferenceFilter(project))
            // The filter rides on the descriptor above; this overload still requires a consumer.
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

    private fun BuildProgress<BuildProgressDescriptor>.reportProblems(problems: List<XMakeProblem>) {
        problems.forEach { problem ->
            val position = problem.filePosition()
            val kind = problem.messageKind()
            val text = problem.message.orEmpty()
            if (position == null) {
                message("XMake", text, kind, null)
            } else {
                fileMessage(position.path?.fileName?.toString() ?: "XMake", text, kind, position)
            }
        }
    }

    private fun XMakeProblem.filePosition(): FilePosition? {
        val path = navigablePath ?: return null
        val sourceLine = (line - 1).coerceAtLeast(0)
        val sourceColumn = (column - 1).coerceAtLeast(0)
        return FilePosition(path.normalize(), sourceLine, sourceColumn)
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

}

/** The stop handle of the running build, put on every run context; invoking it stops that build
 *  on the same path as the Build view stop button. */
val BUILD_STOP_HANDLE: Key<() -> Unit> = Key.create("XMake.Build.Stop")
