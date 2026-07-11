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
 * @file        XMakeConfiguration.kt
 *
 */
package io.xmake.shared

import com.intellij.execution.RunManager
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import io.xmake.project.toolkit.xmakeActiveToolkit
import io.xmake.run.XMakeRunConfiguration
import io.xmake.utils.exception.XMakeRunConfigurationNotSetException
import io.xmake.project.xmakeSettings

@Service(Service.Level.PROJECT)
class XMakeConfiguration(val project: Project) {

    val configuration: XMakeRunConfiguration
        get() {
            return RunManager.getInstance(project).selectedConfiguration?.configuration as? XMakeRunConfiguration
                ?: throw XMakeRunConfigurationNotSetException()
        }

    // the build command line
    val buildCommandLine: GeneralCommandLine
        get() {

            // make parameters — build the currently selected target (mirrors runCommandLine):
            // "all" builds every target, "default"/empty builds the default group, otherwise
            // build just the named target. Options must precede the positional target, so the
            // command reads `xmake build -y [-v] <target>`.
            val target = configuration.runTarget
            val hasNamedTarget = target.isNotEmpty() && target != "default" && target != "all"
            val parameters = mutableListOf<String>()
            if (target == "all" || hasNamedTarget) {
                parameters.add("build")
            }
            parameters.add("-y")
            if (configuration.enableVerbose) {
                parameters.add("-v")
            }
            when {
                target == "all" -> parameters.add("-a")
                hasNamedTarget -> parameters.add(target)
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the rebuild command line
    val rebuildCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("-r", "-y")
            if (configuration.enableVerbose) {
                parameters.add("-v")
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the clean command line
    val cleanCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("c")
            if (configuration.enableVerbose) {
                parameters.add("-v")
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the clean configuration command line
    val cleanConfigurationCommandLine: GeneralCommandLine
        get() {
            val settings = project.xmakeSettings.state

            // make parameters
            val parameters = mutableListOf("f", "-c", "-y")
            if (settings.verbose) {
                parameters.add("-v")
            }
            if (settings.buildDirectory != "") {
                parameters.add("-o")
                parameters.add(settings.buildDirectory)
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the configuration command line — reads the project-level xmake configuration (XMakeSettings),
    // NOT the selected run configuration, so `xmake f` works with a CLion-native run config selected.
    val configurationCommandLine: GeneralCommandLine
        get() {
            val settings = project.xmakeSettings.state

            // make parameters
            val parameters =
                mutableListOf(
                    "f",
                    "-y",
                    "-m",
                    settings.buildMode
                )
            if (settings.platform != "default" && settings.platform.isNotEmpty()) {
                parameters.addAll(listOf("-p", settings.platform))
            }
            if (settings.architecture != "default" && settings.architecture.isNotEmpty()) {
                parameters.addAll(listOf("-a", settings.architecture))
            }
            if (settings.toolchain != "default" && settings.toolchain.isNotEmpty()) {
                parameters.add("--toolchain=${settings.toolchain}")
            }
            if (settings.platform == "android" && settings.androidNDKDirectory != "") {
                parameters.add("--ndk=\"${settings.androidNDKDirectory}\"")
            }
            if (settings.verbose) {
                parameters.add("-v")
            }
            if (settings.buildDirectory != "") {
                parameters.add("-o")
                parameters.add(settings.buildDirectory)
            }
            if (settings.additionalConfiguration != "") {
                parameters.addAll(ParametersListUtil.parse(settings.additionalConfiguration))
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the quick start command line
    val quickStartCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("f", "-y")
            if (configuration.enableVerbose) {
                parameters.add("-v")
            }

            // make command line
            return makeCommandLine(parameters)
        }

    val updateCmakeListsCommandLine: GeneralCommandLine
        get() = makeCommandLine(mutableListOf("project", "-k", "cmake", "-y"))

    val updateCompileCommandsLine: GeneralCommandLine
        get() {
            val parameters = mutableListOf("project", "-k", "compile_commands", "--lsp=clangd")
            val outputDir = project.xmakeSettings.state.compileCommandsPath
            if (outputDir.isNotEmpty()) {
                parameters.add(outputDir)
            }
            return makeCommandLine(parameters)
        }


    // configuration is changed?
    var changed = true

    // make command line
    fun makeCommandLine(
        parameters: List<String>,
        environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT
    ): GeneralCommandLine {

        // make command — the xmake binary and working directory come from the project-level active
        // toolkit / project root, not the selected run configuration, so this works in the native flow.
        return GeneralCommandLine(project.xmakeActiveToolkit?.path ?: "xmake")
            .withParameters(parameters)
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(project.basePath)
            .withEnvironment(environmentVariables.envs)
            .withRedirectErrorStream(true)
    }

    /*    // ensure state
        private fun ensureState() {
            if (configuration.runArchitecture == "" && architectures.isNotEmpty()) {
                configuration.runArchitecture = architectures[0]
            }
        }*/
}

val Project.xmakeConfiguration: XMakeConfiguration
    get() = this.getService(XMakeConfiguration::class.java)
        ?: error("Failed to get XMakeConfiguration for $this")

val Project.xmakeConfigurationOrNull: XMakeConfiguration?
    get() = this.getService(XMakeConfiguration::class.java) ?: null

