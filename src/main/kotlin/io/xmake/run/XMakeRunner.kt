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
 * @author      ruki
 * @file        XMakeRunner.kt
 *
 */
package io.xmake.run

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.showRunContent
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerManager
import io.xmake.debug.XMakeDebugSupport
import io.xmake.debug.prepareXMakeDebugBuild
import io.xmake.debug.prepareXMakeDebugLaunch
import io.xmake.project.console.XMakeConsole
import io.xmake.project.console.xmakeConsoleService
import io.xmake.run.command.XMakeExecutionService
import io.xmake.run.command.xmakeExecutionService
import io.xmake.run.state.XMakeDebugState
import io.xmake.run.state.XMakeRunState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.toPromiseWithoutLogError

class XMakeRunner : AsyncProgramRunner<RunnerSettings>() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean =
        profile is XMakeRunConfiguration && when (executorId) {
            DefaultRunExecutor.EXECUTOR_ID -> true
            DefaultDebugExecutor.EXECUTOR_ID -> XMakeDebugSupport.isAvailable()
            else -> false
        }

    override fun getRunnerId(): String = "XMakeRunner"

    override fun execute(
        environment: ExecutionEnvironment,
        state: RunProfileState,
    ): Promise<RunContentDescriptor?> {
        val project = environment.project
        val execution = project.xmakeExecutionService
        val deferred = when (state) {
            is XMakeRunState -> execution.submit {
                val console = prepareConsole(project)
                executeRun(execution, state, environment, console)
            }
            // The build runs through ProjectTaskManager *before* the mutex is acquired: it takes
            // the mutex itself internally, and the mutex is not reentrant (see
            // XMakeExecutionService.submitAfter).
            is XMakeDebugState -> execution.submitAfter(
                before = { prepareXMakeDebugBuild(project, state) },
                task = {
                    val console = prepareConsole(project)
                    executeDebug(execution, state, environment, console)
                },
            )
            else -> execution.submit {
                throw ExecutionException("Unsupported XMake run profile state: ${state::class.java.name}")
            }
        }
        return deferred.toPromiseWithoutLogError()
    }

    private suspend fun executeRun(
        execution: XMakeExecutionService,
        state: XMakeRunState,
        environment: ExecutionEnvironment,
        console: XMakeConsole,
    ): RunContentDescriptor {
        execution.execute(console, state.configureCommand)

        val processHandler = state.startProcess()
        return try {
            withContext(Dispatchers.EDT) {
                ensureProjectIsOpen(environment.project)
                showRunContent(
                    state.createExecutionResult(environment.executor, processHandler),
                    environment,
                ) ?: throw ExecutionException("Failed to create the XMake run descriptor")
            }
        } catch (error: Throwable) {
            processHandler.stopAfterFailedStart(error)
            throw error
        }
    }

    private suspend fun executeDebug(
        execution: XMakeExecutionService,
        state: XMakeDebugState,
        environment: ExecutionEnvironment,
        @Suppress("UNUSED_PARAMETER") console: XMakeConsole,
    ): RunContentDescriptor {
        val debugSupport = XMakeDebugSupport.find()
            ?: throw ExecutionException("XMake debug support is not available in this IDE")
        val launch = prepareXMakeDebugLaunch(state, environment.project, execution)

        return withContext(Dispatchers.EDT) {
            ensureProjectIsOpen(environment.project)
            val starter = debugSupport.createProcessStarter(launch, environment)
            val session = XDebuggerManager.getInstance(environment.project)
                .newSessionBuilder(starter)
                .environment(environment)
                .startSession()
            session.runContentDescriptor ?: run {
                val error = ExecutionException("Failed to create the XMake debug descriptor")
                runCatching { session.session.stop() }
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
                throw error
            }
        }
    }

    private suspend fun prepareConsole(project: Project): XMakeConsole {
        val console = project.xmakeConsoleService.awaitReady()
        withContext(Dispatchers.EDT) {
            ensureProjectIsOpen(project)
            FileDocumentManager.getInstance().saveAllDocuments()
            console.clear()
        }
        return console
    }

    private fun ensureProjectIsOpen(project: Project) {
        if (project.isDisposed) {
            throw ExecutionException("Project was disposed before XMake could start")
        }
    }

    private fun ProcessHandler.stopAfterFailedStart(failure: Throwable) {
        if (!isStartNotified) {
            runCatching { startNotify() }.exceptionOrNull()?.let(failure::addSuppressed)
        }
        if (!isProcessTerminated && !isProcessTerminating) {
            runCatching { destroyProcess() }.exceptionOrNull()?.let(failure::addSuppressed)
        }
    }
}
