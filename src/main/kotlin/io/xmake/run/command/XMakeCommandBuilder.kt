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
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.project.toolkit.ToolkitHostType.LOCAL
import io.xmake.project.toolkit.ToolkitHostType.SSH
import io.xmake.project.toolkit.ToolkitHostType.WSL
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

internal class XMakeCommandBuilder private constructor(
    private val toolkit: Toolkit,
    private val workingDirectory: String,
    private val parameters: List<String> = emptyList(),
    private val environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
    private val environmentOverrides: Map<String, String> = emptyMap(),
    private val configurationDirectory: String,
) {

    fun parameters(values: Iterable<String>): XMakeCommandBuilder =
        copy(parameters = parameters + values)

    fun environmentVariables(data: EnvironmentVariablesData): XMakeCommandBuilder =
        copy(environmentVariables = data)

    fun environmentOverrides(values: Map<String, String>): XMakeCommandBuilder =
        copy(environmentOverrides = environmentOverrides + values)

    fun build(): XMakeCommand {
        val commandLine = GeneralCommandLine(toolkit.path)
            .withParameters(parameters)
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(workingDirectory)
            .withRedirectErrorStream(false)

        environmentVariables.configureCommandLine(commandLine, true)
        // Must stay after configureCommandLine: these overrides win over user environment variables.
        commandLine.environment.putAll(environmentOverrides)
        return XMakeCommand(
            commandLine = commandLine,
            toolkit = toolkit,
            workingDirectory = workingDirectory,
            configurationDirectory = configurationDirectory,
        )
    }

    private fun copy(
        parameters: List<String> = this.parameters,
        environmentVariables: EnvironmentVariablesData = this.environmentVariables,
        environmentOverrides: Map<String, String> = this.environmentOverrides,
        configurationDirectory: String = this.configurationDirectory,
    ): XMakeCommandBuilder = XMakeCommandBuilder(
        toolkit,
        workingDirectory,
        parameters,
        environmentVariables,
        environmentOverrides,
        configurationDirectory,
    )

    companion object {
        fun forBuildProfile(
            profileId: String,
            toolkit: Toolkit,
            workingDirectory: String,
            configureArguments: List<String>,
        ): XMakeCommandBuilder {
            require(XMakeBuildProfile.isValidId(profileId)) {
                "Invalid XMake build profile ID: $profileId"
            }
            val configurationDirectory = hostPath(
                toolkit.host.type,
                workingDirectory,
                ".idea/xmake/profiles/$profileId/${configurationHash(toolkit, workingDirectory, configureArguments)}",
            )
            return XMakeCommandBuilder(
                toolkit,
                workingDirectory,
                // The env copy is for the xmake process; the field is the plugin-side truth.
                environmentOverrides = mapOf(XMAKE_CONFIG_DIRECTORY_ENV to configurationDirectory),
                configurationDirectory = configurationDirectory,
            )
        }

        private fun hostPath(hostType: ToolkitHostType, basePath: String, relativePath: String): String =
            when (hostType) {
                LOCAL -> Path.of(basePath).resolve(relativePath).toString()
                WSL, SSH -> "${basePath.trimEnd('/')}/$relativePath"
            }

        private fun configurationHash(
            toolkit: Toolkit,
            workingDirectory: String,
            configureArguments: List<String>,
        ): String {
            val identity = buildString {
                appendField("xmake-configuration-v1")
                appendField(toolkit.host.id.canonical)
                appendField(toolkit.path)
                appendField(toolkit.version)
                appendField(workingDirectory)
                configureArguments.forEach { argument -> appendField(argument) }
            }
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(UTF_8)),
            )
        }

        private fun StringBuilder.appendField(value: String) {
            append(value.length).append(':').append(value)
        }
    }
}
