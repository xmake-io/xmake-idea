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
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.project.toolkit.ToolkitHostType.LOCAL
import io.xmake.project.toolkit.ToolkitHostType.SSH
import io.xmake.project.toolkit.ToolkitHostType.WSL
import io.xmake.project.xmakeSettings
import io.xmake.utils.SystemUtils
import io.xmake.utils.cli.XMakeCommandLine
import io.xmake.utils.cli.DEFAULT_TARGET
import io.xmake.utils.cli.QUERY_ENVIRONMENT
import io.xmake.utils.cli.xmake
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat

/** Builds commands from one project-owned build profile captured at construction time. */
internal class XMakeCommandFactory(
    private val project: Project,
    private val profile: XMakeBuildProfile,
    toolkit: Toolkit? = null,
) {
    private val compileCommandsPath = project.xmakeSettings.state.compileCommandsPath
    val compileCommandsGeneratedPath: String = compileCommandsGeneratedPath(compileCommandsPath)
    private val compileCommandsOutputDirectory: String? = compileCommandsOutputDirectory(compileCommandsGeneratedPath)
    /** The toolkit every command from this factory runs on: the preview-selected installation,
     *  or the profile toolkit. Construction throws RuntimeConfigurationError when it is absent
     *  or unavailable. */
    val toolkit: Toolkit = toolkit ?: profile.resolveToolkit(project)
        ?.takeIf { it.isAvailable }
        ?: throw RuntimeConfigurationError(
            "XMake toolkit is not set, is unavailable in this project, or is no longer registered",
        )

    private val buildDirectoryOption = profile.buildDirectory.takeIf { it.isNotEmpty() }

    private val configureArguments: List<String> = xmake(this.toolkit).apply {
        args("-m", profile.buildMode)
        option("-p" to profile.platform.takeUnless { it == XMakeBuildProfile.USE_XMAKE_DEFAULT })
        option("-a" to profile.architecture.takeUnless { it == XMakeBuildProfile.USE_XMAKE_DEFAULT })
        if (profile.toolchain != XMakeBuildProfile.USE_XMAKE_DEFAULT) {
            args("--toolchain=${profile.toolchain}")
        }
        if (profile.platform == ANDROID_PLATFORM && profile.androidNdkDirectory.isNotEmpty()) {
            args("--ndk=${profile.androidNdkDirectory}")
        }
        option("-o" to buildDirectoryOption)
        if (profile.configureArguments.isNotEmpty()) {
            parsedArgs(profile.configureArguments)
        }
    }.arguments

    private val projectDirectory: String = project.xmakeProjectDirectories.resolveProjectDirectory(this.toolkit)
    private val configurationDirectory: String = profileConfigurationDirectory(
        profile.id,
        this.toolkit,
        projectDirectory,
        configureArguments,
    )

    fun createBuild(): XMakeCommand = createTargetBuild(DEFAULT_TARGET)

    fun createTargetBuild(targetName: String): XMakeCommand = createCommand {
        build {
            target = targetName
            verbose = profile.verbose
        }
    }

    fun createRebuild(): XMakeCommand = createCommand {
        build {
            rebuild = true
            verbose = profile.verbose
        }
    }

    fun createClean(): XMakeCommand = createCommand {
        // `clean -a` also removes the cached configuration for the profile, which a
        // subsequent configure rebuilds.
        clean {
            all = true
            verbose = profile.verbose
        }
    }

    fun createCleanConfiguration(): XMakeCommand = createCommand {
        config {
            clean = true
            yes = true
            verbose = profile.verbose
            outputDirectory = buildDirectoryOption
        }
    }

    fun createConfigure(): XMakeCommand = createCommand {
        config {
            yes = true
            arguments = configureArguments
            verbose = profile.verbose
        }
    }

    fun createUpdateCMakeLists(): XMakeCommand = createCommand {
        project {
            kind = "cmake"
            yes = true
        }
    }

    fun createUpdateCompileCommands(): XMakeCommand = createCommand {
        project {
            kind = "compile_commands"
            lsp = "clangd"
            outputDirectory = compileCommandsOutputDirectory
        }
    }

    fun createRun(
        targetName: String,
        arguments: String,
        launchWorkingDirectory: String,
        environment: EnvironmentVariablesData,
    ): XMakeCommand {
        val resolvedLaunchWorkingDirectory = resolveLaunchWorkingDirectory(
            project,
            toolkit,
            launchWorkingDirectory,
        )
        return createCommand(
            environmentVariables = environment,
        ) {
            run {
                workingDirectory = resolvedLaunchWorkingDirectory
                target = targetName
                parsedArguments = arguments
            }
        }
    }

    fun createTargetPathQuery(targetName: String): XMakeCommand {
        val scriptPath = SystemUtils.getScriptPath("targetpath.lua")
            ?: throw ExecutionException("The target path script was not found")
        return createCommand(environmentOverrides = QUERY_ENVIRONMENT) {
            lua {
                script = scriptPath
                target = targetName
            }
        }
    }

    fun createInfoQuery(queryName: String, jsonOutput: Boolean = true): XMakeCommand =
        createCommand(environmentOverrides = QUERY_ENVIRONMENT) {
            show(queryName, json = jsonOutput)
        }

    private fun createCommand(
        environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT,
        environmentOverrides: Map<String, String> = emptyMap(),
        configure: XMakeCommandLine.() -> Unit,
    ): XMakeCommand {
        val line = xmake(toolkit)
            .apply(configure)
            .withWorkDirectory(projectDirectory)
            .withRedirectErrorStream(false)
        environmentVariables.configureCommandLine(line, true)
        // Must stay after configureCommandLine, which writes the user environment block.
        line.environment.putAll(mapOf(XMAKE_CONFIG_DIRECTORY_ENV to configurationDirectory) + environmentOverrides)
        return XMakeCommand(line, toolkit, projectDirectory, configurationDirectory)
    }

    private companion object {
        private const val ANDROID_PLATFORM = "android"

        /** Generated file path relative to the project directory; xmake always names the file
         *  compile_commands.json, so only the directory part of [setting] applies. */
        private fun compileCommandsGeneratedPath(setting: String): String {
            val normalized = setting.trim().replace('\\', '/').trimEnd('/')
            if (normalized.isEmpty()) return "compile_commands.json"
            val directory = normalized.substringBeforeLast('/', "")
            return if (directory.isEmpty()) "compile_commands.json" else "$directory/compile_commands.json"
        }

        private fun compileCommandsOutputDirectory(generatedPath: String): String? =
            generatedPath.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }

        private fun profileConfigurationDirectory(
            profileId: String,
            toolkit: Toolkit,
            workingDirectory: String,
            configureArguments: List<String>,
        ): String {
            require(XMakeBuildProfile.isValidId(profileId)) {
                "Invalid XMake build profile ID: $profileId"
            }
            return hostPath(
                toolkit.host.type,
                workingDirectory,
                ".idea/xmake/profiles/$profileId/${configurationHash(toolkit, workingDirectory, configureArguments)}",
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

/** The project-scoped entry for building xmake commands outside the command context of
 *  one build profile ([withProfileCommands]). */
internal fun Project.xmakeCommands(profile: XMakeBuildProfile): XMakeCommandFactory =
    XMakeCommandFactory(this, profile)
