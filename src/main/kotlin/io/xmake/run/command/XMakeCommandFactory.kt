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
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.xmakeSettings
import io.xmake.utils.SystemUtils

/** Builds commands from one project-owned build profile captured at construction time. */
internal class XMakeCommandFactory(
    private val project: Project,
    profile: XMakeBuildProfile,
) {
    private val profileSnapshot = profile.copy()
    private val compileCommandsPath = project.xmakeSettings.state.compileCommandsPath
    private val configureArguments = buildList {
        args("-m", profileSnapshot.buildMode)
        option("-p", profileSnapshot.platform.takeUnless { it == XMakeBuildProfile.USE_XMAKE_DEFAULT })
        option("-a", profileSnapshot.architecture.takeUnless { it == XMakeBuildProfile.USE_XMAKE_DEFAULT })
        if (profileSnapshot.toolchain != XMakeBuildProfile.USE_XMAKE_DEFAULT) {
            args("--toolchain=${profileSnapshot.toolchain}")
        }
        if (profileSnapshot.platform == ANDROID_PLATFORM && profileSnapshot.androidNdkDirectory.isNotEmpty()) {
            args("--ndk=${profileSnapshot.androidNdkDirectory}")
        }
        option("-o", profileSnapshot.buildDirectory.takeIf { it.isNotEmpty() })
        if (profileSnapshot.configureArguments.isNotEmpty()) {
            parsedArgs(profileSnapshot.configureArguments)
        }
    }
    private val toolkit: Toolkit = profileSnapshot.resolveToolkit(project)
        ?: throw RuntimeConfigurationError(
            "XMake toolkit is not set, is unavailable in this project, or is no longer registered",
        )

    private val commandBuilder = run {
        if (!toolkit.isAvailable) {
            throw RuntimeConfigurationError("XMake toolkit is unavailable in this project")
        }
        val projectDirectory = project.xmakeProjectDirectories.resolveProjectDirectory(toolkit)
        XMakeCommandBuilder.forBuildProfile(profileSnapshot.id, toolkit, projectDirectory, configureArguments)
    }

    fun createBuild(): XMakeCommand = createTargetBuild(DEFAULT_BUILD_TARGET)

    fun createTargetBuild(targetName: String): XMakeCommand = createCommand {
        args("build", "-y")
        flag("-v", profileSnapshot.verbose)
        appendTarget(targetName)
    }

    fun createRebuild(): XMakeCommand = createCommand {
        args("build", "-r", "-y")
        flag("-v", profileSnapshot.verbose)
    }

    fun createClean(): XMakeCommand = createCommand {
        args("clean")
        flag("-v", profileSnapshot.verbose)
    }

    fun createCleanConfiguration(): XMakeCommand = createCommand {
        args("config", "-c", "-y")
        flag("-v", profileSnapshot.verbose)
        option("-o", profileSnapshot.buildDirectory.takeIf { it.isNotEmpty() })
    }

    fun createConfigure(): XMakeCommand = createCommand {
        args("config", "-y")
        args(configureArguments)
        flag("-v", profileSnapshot.verbose)
    }

    fun createUpdateCMakeLists(): XMakeCommand = createCommand {
        args("project", "-k", "cmake", "-y")
    }

    fun createUpdateCompileCommands(): XMakeCommand = createCommand {
        args("project", "-k", "compile_commands", "--lsp=clangd")
        compileCommandsPath
            .takeIf { it.isNotEmpty() }
            ?.let { args(it) }
    }

    fun createRun(
        targetName: String,
        arguments: String,
        environment: EnvironmentVariablesData,
    ): XMakeCommand = createCommand(
        environmentVariables = environment,
    ) {
        args("run")
        appendTarget(targetName)
        if (arguments.isNotEmpty()) {
            parsedArgs(arguments)
            }
    }

    fun createTargetPathQuery(targetName: String): XMakeCommand {
        val scriptPath = SystemUtils.getScriptPath("targetpath.lua")
            ?: throw ExecutionException("The target path script was not found")
        return createCommand(environmentOverrides = QUERY_ENVIRONMENT) {
            args("l", scriptPath)
            targetName
                .takeUnless { it == DEFAULT_BUILD_TARGET || it.isBlank() }
                ?.let { args(it) }
        }
    }

    fun createInfoQuery(queryName: String): XMakeCommand {
        require(INFO_QUERY_PATTERN.matches(queryName)) { "Invalid XMake info query: $queryName" }
        return createCommand(environmentOverrides = QUERY_ENVIRONMENT) {
            args("show", "-l", queryName, "--json")
        }
    }

    private fun createCommand(
        environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
        environmentOverrides: Map<String, String> = emptyMap(),
        parameters: MutableList<String>.() -> Unit,
    ): XMakeCommand = commandBuilder
        .parameters(buildList(parameters))
        .environmentVariables(environmentVariables)
        .environmentOverrides(environmentOverrides)
        .build()

    private fun MutableList<String>.appendTarget(name: String) {
        when (name) {
            "all" -> args("-a")
            "", DEFAULT_BUILD_TARGET -> Unit
            else -> args(name)
        }
    }

    private companion object {
        private const val ANDROID_PLATFORM = "android"

        val INFO_QUERY_PATTERN = Regex("[a-z]+")

        val QUERY_ENVIRONMENT = mapOf(
            "XMAKE_SKIP_HISTORY" to "1",
            "XMAKE_ROOT" to "y",
            "XMAKE_COLOR_TERM" to "nocolor",
        )

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
