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
package io.xmake.run.state

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import io.xmake.project.console.xmakeConsoleService
import io.xmake.run.XMakeRunConfiguration
import io.xmake.run.command.XMakeCommand
import io.xmake.run.command.XMakeCommandFactory
import io.xmake.run.command.XMakeConsoleOptions
import io.xmake.run.command.XMakeCommandProcessHandler
import io.xmake.run.target.requireXMakeBuildProfileFor

internal class XMakeRunState private constructor(
    environment: ExecutionEnvironment,
    val configureCommand: XMakeCommand,
    private val runCommand: XMakeCommand,
) : CommandLineState(environment) {

    public override fun startProcess(): ProcessHandler {
        val project = environment.project
        return XMakeCommandProcessHandler(
            project,
            runCommand,
            XMakeConsoleOptions(showProblems = true, showExitCode = true),
            onProblems = { problems ->
                project.xmakeConsoleService.whenReady { console ->
                    console.updateProblems(problems)
                }
            },
        ).processHandler
    }

    internal fun createExecutionResult(
        executor: Executor,
        processHandler: ProcessHandler,
    ): ExecutionResult {
        val console = createConsole(executor)
        console?.attachToProcess(processHandler)
        return DefaultExecutionResult(
            console,
            processHandler,
            *createActions(console, processHandler, executor),
        )
    }

    companion object {
        fun create(
            configuration: XMakeRunConfiguration,
            environment: ExecutionEnvironment,
        ): XMakeRunState {
            val profile = configuration.project.requireXMakeBuildProfileFor(environment.executionTarget)
            val commandFactory = XMakeCommandFactory(configuration.project, profile)
            return XMakeRunState(
                environment,
                commandFactory.createConfigure(),
                commandFactory.createRun(
                    configuration.runTarget,
                    configuration.runArguments,
                    configuration.runEnvironment,
                ),
            )
        }
    }
}
