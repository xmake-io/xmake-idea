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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.run.XMakeRunConfiguration
import io.xmake.utils.exception.XMakeRunConfigurationNotSetException

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

            // make parameters
            val parameters = mutableListOf("-y")
            if (configuration.enableVerbose) {
                parameters.add("-v")
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

            // make parameters
            val parameters = mutableListOf("f", "-c", "-y")
            if (configuration.enableVerbose) {
                parameters.add("-v")
            }
            if (configuration.buildDirectory != "") {
                parameters.add("-o")
                parameters.add(configuration.buildDirectory)
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the configuration command line
    val configurationCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters =
                mutableListOf(
                    "f",
                    "-y",
                    "-m",
                    configuration.runMode
                )
            if (configuration.runPlatform != "default") {
                parameters.addAll(listOf("-p", configuration.runPlatform))
            }
            if (configuration.runArchitecture != "default") {
                parameters.addAll(listOf("-a", configuration.runArchitecture))
            }
            if (configuration.runToolchain != "default" ) {
                parameters.add("--toolchain=${configuration.runToolchain}")
            }
            if (configuration.runPlatform == "android" && configuration.androidNDKDirectory != "") {
                parameters.add("--ndk=\"${configuration.androidNDKDirectory}\"")
            }
            if (configuration.enableVerbose) {
                parameters.add("-v")
            }
            if (configuration.buildDirectory != "") {
                parameters.add("-o")
                parameters.add(configuration.buildDirectory)
            }
            if (configuration.additionalConfiguration != "") {
                parameters.addAll(ParametersListUtil.parse(configuration.additionalConfiguration))
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
        get() = makeCommandLine(mutableListOf("project", "-k", "compile_commands"))


    // configuration is changed?
    var changed = true

    // make command line
    fun makeCommandLine(
        parameters: List<String>,
        environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT
    ): GeneralCommandLine {

        // make command
        return GeneralCommandLine(project.activatedToolkit!!.path)
            .withParameters(parameters)
            .withCharset(Charsets.UTF_8)
            // Todo: Check if correct.
            .withWorkDirectory(
                configuration.runWorkingDir
            )
            .withEnvironment(environmentVariables.envs)
            .withRedirectErrorStream(true)
    }

    /*    // ensure state
        private fun ensureState() {
            if (configuration.runArchitecture == "" && architectures.isNotEmpty()) {
                configuration.runArchitecture = architectures[0]
            }
        }*/

    companion object {
        // get log
        private val Log = Logger.getInstance(XMakeConfiguration::class.java.getName())
    }
}

val Project.xmakeConfiguration: XMakeConfiguration
    get() = this.getService(XMakeConfiguration::class.java)
        ?: error("Failed to get XMakeConfiguration for $this")

val Project.xmakeConfigurationOrNull: XMakeConfiguration?
    get() = this.getService(XMakeConfiguration::class.java) ?: null

