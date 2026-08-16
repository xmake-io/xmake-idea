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

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ProgramRunner
import com.intellij.util.execution.ParametersListUtil
import io.xmake.run.XMakeRunConfiguration
import io.xmake.run.command.XMakeCommand
import io.xmake.run.command.XMakeCommandFactory

internal class XMakeDebugState private constructor(
    val configureCommand: XMakeCommand,
    val buildCommand: XMakeCommand,
    val targetPathCommand: XMakeCommand,
    val targetName: String,
    val buildMode: String,
    val configuredDapDriverPath: String,
    val detectDapDriver: Boolean,
    val launchConfiguration: String,
    val arguments: List<String>,
    val environment: Map<String, String>,
) : RunProfileState {

    // XMakeRunner consumes this state asynchronously and returns the XDebugger descriptor.
    override fun execute(executor: Executor, runner: ProgramRunner<*>) =
        throw ExecutionException("XMakeDebugState must be executed by XMakeRunner")

    companion object {
        fun create(configuration: XMakeRunConfiguration): XMakeDebugState {
            val commands = XMakeCommandFactory(configuration)
            val buildCommand = commands.createTargetBuild()
            if (buildCommand.toolkit.requiresBackend) {
                throw ExecutionException("Remote XMake toolkits are not supported for debugging")
            }

            return XMakeDebugState(
                configureCommand = commands.createConfigure(),
                buildCommand = buildCommand,
                targetPathCommand = commands.createTargetPathQuery(),
                targetName = configuration.runTarget,
                buildMode = configuration.runMode,
                configuredDapDriverPath = configuration.dapDriverPath,
                detectDapDriver = configuration.dapDriverAutoDetect,
                launchConfiguration = configuration.launchConfiguration,
                arguments = ParametersListUtil.parse(configuration.runArguments),
                environment = configuration.runEnvironment.envs.toMap(),
            )
        }
    }
}
