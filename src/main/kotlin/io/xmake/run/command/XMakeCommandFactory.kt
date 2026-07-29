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
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.util.execution.ParametersListUtil
import io.xmake.project.xmakeSettings
import io.xmake.run.XMakeRunConfiguration
import io.xmake.utils.SystemUtils

/** Builds commands from one run configuration captured at construction time. */
internal class XMakeCommandFactory(configuration: XMakeRunConfiguration) {
    private val target = configuration.runTarget
    private val platform = configuration.runPlatform
    private val architecture = configuration.runArchitecture
    private val toolchain = configuration.runToolchain
    private val mode = configuration.runMode
    private val arguments = configuration.runArguments
    private val environment = configuration.runEnvironment
    private val buildDirectory = configuration.buildDirectory
    private val androidNdkDirectory = configuration.androidNDKDirectory
    private val verbose = configuration.enableVerbose
    private val additionalConfiguration = configuration.additionalConfiguration
    private val compileCommandsPath = configuration.project.xmakeSettings.state.compileCommandsPath
    private val configureOptions = commandArguments {
        args("-m", mode)
        option("-p", platform.takeUnless { it == DEFAULT_VALUE })
        option("-a", architecture.takeUnless { it == DEFAULT_VALUE })
        if (toolchain != DEFAULT_VALUE) {
            args("--toolchain=$toolchain")
        }
        if (platform == "android" && androidNdkDirectory.isNotEmpty()) {
            args("--ndk=$androidNdkDirectory")
        }
        option("-o", buildDirectory.takeIf { it.isNotEmpty() })
        if (additionalConfiguration.isNotEmpty()) {
            parsedArgs(additionalConfiguration)
        }
    }
    private val commandBuilder = XMakeCommandBuilder.forConfiguration(configuration, configureOptions)

    fun createBuild(): XMakeCommand = createTargetBuild(DEFAULT_VALUE)

    fun createTargetBuild(target: String = this.target): XMakeCommand = createCommand {
        args("build", "-y")
        flag("-v", verbose)
        target(target)
    }

    fun createRebuild(): XMakeCommand = createCommand {
        args("build", "-r", "-y")
        flag("-v", verbose)
    }

    fun createClean(): XMakeCommand = createCommand {
        args("clean")
        flag("-v", verbose)
    }

    fun createCleanConfiguration(): XMakeCommand = createCommand {
        args("config", "-c", "-y")
        flag("-v", verbose)
        option("-o", buildDirectory.takeIf { it.isNotEmpty() })
    }

    fun createConfigure(): XMakeCommand = createCommand {
        args("config", "-y")
        args(configureOptions)
        flag("-v", verbose)
    }

    fun createUpdateCmakeLists(): XMakeCommand = createCommand {
        args("project", "-k", "cmake", "-y")
    }

    fun createUpdateCompileCommands(): XMakeCommand = createCommand {
        args("project", "-k", "compile_commands", "--lsp=clangd")
        compileCommandsPath
            .takeIf { it.isNotEmpty() }
            ?.let { args(it) }
    }

    fun createRun(): XMakeCommand = createCommand(
        environmentVariables = environment,
    ) {
        args("run")
        target(target)
        if (arguments.isNotEmpty()) {
            parsedArgs(arguments)
        }
    }

    fun createTargetPathQuery(): XMakeCommand {
        val scriptPath = SystemUtils.getScriptPath("targetpath.lua")
            ?: throw ExecutionException("The target path script was not found")
        return createCommand(environmentOverrides = TARGET_QUERY_ENVIRONMENT) {
            args("l", scriptPath)
            target
                .takeUnless { it == DEFAULT_VALUE || it.isBlank() }
                ?.let { args(it) }
        }
    }

    private fun createCommand(
        environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
        environmentOverrides: Map<String, String> = emptyMap(),
        parameters: MutableList<String>.() -> Unit,
    ): XMakeCommand = commandBuilder
        .parameters(commandArguments(parameters))
        .environment(environmentVariables)
        .overrideEnvironment(environmentOverrides)
        .build()

    private fun MutableList<String>.target(value: String) {
        when (value) {
            "all" -> args("-a")
            "", DEFAULT_VALUE -> Unit
            else -> args(value)
        }
    }

    private companion object {
        const val DEFAULT_VALUE = "default"

        val TARGET_QUERY_ENVIRONMENT = mapOf(
            "XMAKE_SKIP_HISTORY" to "1",
            "XMAKE_ROOT" to "y",
            "XMAKE_COLOR_TERM" to "nocolor",
        )

        fun commandArguments(block: MutableList<String>.() -> Unit): List<String> = buildList(block)
    }
}

private fun MutableList<String>.args(vararg values: String) {
    addAll(values)
}

private fun MutableList<String>.args(values: Iterable<String>) {
    addAll(values)
}

private fun MutableList<String>.option(name: String, value: String?) {
    if (value != null) args(name, value)
}

private fun MutableList<String>.flag(name: String, enabled: Boolean) {
    if (enabled) args(name)
}

private fun MutableList<String>.parsedArgs(commandLine: String) {
    addAll(ParametersListUtil.parse(commandLine))
}
