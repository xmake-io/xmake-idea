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

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RuntimeConfigurationError
import io.xmake.project.toolkit.Toolkit
import io.xmake.run.XMakeRunConfiguration
import io.xmake.utils.exception.XMakeToolkitNotSetException
import io.xmake.utils.path.WorkingDirectoryResolver
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.HexFormat

internal class XMakeCommandBuilder private constructor(
    private val toolkit: Toolkit,
    private val workingDirectory: String,
    private val parameters: List<String> = emptyList(),
    private val environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
    private val environmentOverrides: Map<String, String> = emptyMap(),
) {

    fun parameters(values: Iterable<String>): XMakeCommandBuilder =
        copy(parameters = parameters + values)

    fun environment(data: EnvironmentVariablesData): XMakeCommandBuilder =
        copy(environmentVariables = data)

    fun overrideEnvironment(values: Map<String, String>): XMakeCommandBuilder =
        copy(environmentOverrides = environmentOverrides + values)

    fun build(): XMakeCommand {
        val commandLine = GeneralCommandLine(toolkit.path)
            .withParameters(parameters)
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(workingDirectory)
            .withRedirectErrorStream(false)

        environmentVariables.configureCommandLine(commandLine, true)
        commandLine.withEnvironment(environmentOverrides)
        return XMakeCommand(commandLine, toolkit, workingDirectory)
    }

    private fun copy(
        parameters: List<String> = this.parameters,
        environmentVariables: EnvironmentVariablesData = this.environmentVariables,
        environmentOverrides: Map<String, String> = this.environmentOverrides,
    ): XMakeCommandBuilder = XMakeCommandBuilder(
        toolkit,
        workingDirectory,
        parameters,
        environmentVariables,
        environmentOverrides,
    )

    companion object {
        /**
         * XMake stores configure state on disk, while build/run do not accept
         * the configure options. Give each build configuration its own state
         * root so every command in the factory uses the same explicit context.
         */
        fun forConfiguration(
            configuration: XMakeRunConfiguration,
            configureOptions: List<String>,
        ): XMakeCommandBuilder {
            val toolkit = configuration.runToolkit ?: throw XMakeToolkitNotSetException()
            if (toolkit.path.isBlank()) {
                throw RuntimeConfigurationError("XMake toolkit path is not set")
            }
            if (configuration.runWorkingDir.isBlank()) {
                throw RuntimeConfigurationError("Working directory is not set")
            }
            if (toolkit.isOnRemote && toolkit.host.backend == null) {
                throw RuntimeConfigurationError("XMake ${toolkit.host.type} toolkit host is not available")
            }
            val workingDirectory = WorkingDirectoryResolver.resolve(
                configuration.project,
                configuration.runWorkingDir,
                toolkit,
            )
            val configurationHash = configurationHash(toolkit, workingDirectory, configureOptions)
            val configurationRoot = hostPath(
                workingDirectory,
                ".idea/xmake/configurations/$configurationHash",
            )
            return XMakeCommandBuilder(
                toolkit,
                workingDirectory,
                environmentOverrides = mapOf(XMAKE_CONFIG_DIRECTORY_ENV to configurationRoot),
            )
        }

        private fun configurationHash(
            toolkit: Toolkit,
            workingDirectory: String,
            configureOptions: List<String>,
        ): String {
            val identity = buildString {
                appendField("xmake-configuration-v1")
                appendField(toolkit.host.id.canonical)
                appendField(toolkit.path)
                appendField(toolkit.version)
                appendField(workingDirectory)
                configureOptions.forEach { argument -> appendField(argument) }
            }
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(UTF_8)),
            )
        }

        private fun StringBuilder.appendField(value: String) {
            append(value.length).append(':').append(value)
        }

        private fun hostPath(parent: String, child: String): String =
            "${parent.trimEnd('/', '\\')}/$child"
    }
}
