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
 * @file        XMakeRunConfiguration.kt
 *
 */
package io.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.NopProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.IncorrectOperationException
import io.xmake.utils.path.WorkingDirectoryResolver
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.project.console.xmakeConsoleService
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.SystemUtils
import io.xmake.utils.info.XMakeInfoManager
import io.xmake.utils.info.xmakeInfo
import io.xmake.debug.DapDriverDetector
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

class XMakeRunConfiguration(
    project: Project, name: String, factory: ConfigurationFactory
) : LocatableConfigurationBase<RunProfileState>(project, factory, name),
    RunConfigurationWithSuppressedDefaultDebugAction {

    @OptionTag(tag = "activatedToolkit")
    var runToolkit: Toolkit? = null

    // the run target
    @OptionTag(tag = "target")
    var runTarget: String = "default"

    @OptionTag(tag = "platform")
    var runPlatform: String = if (platforms.contains(SystemUtils.platform())) SystemUtils.platform() else "default"

    @OptionTag(tag = "architecture")
    var runArchitecture: String = getArchitecturesByPlatform(runPlatform).firstOrNull() ?: "default"

    @OptionTag(tag = "toolchain")
    var runToolchain: String = toolchains.firstOrNull() ?: "default"

    @OptionTag(tag = "mode")
    var runMode: String = "release"

    // the run arguments
    @OptionTag(tag = "arguments")
    var runArguments: String = ""

    // the run environmen
    @get:Transient
    var runEnvironment: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    @OptionTag(tag = "workingDirectory")
    var runWorkingDir: String = project.basePath ?: ""

    val resolvedWorkingDirectory: String
        get() = WorkingDirectoryResolver.resolve(project, runWorkingDir, runToolkit)

    @OptionTag(tag = "buildDirectory")
    var buildDirectory: String = ""

    @OptionTag(tag = "androidNDKDirectory")
    var androidNDKDirectory: String = ""

    @OptionTag(tag = "enableVerbose")
    var enableVerbose: Boolean = false

    @OptionTag(tag = "additionalConfiguration")
    var additionalConfiguration: String = ""

    // DAP driver configuration
    @OptionTag(tag = "dapDriverPath")
    var dapDriverPath: String = ""

    @OptionTag(tag = "dapDriverAutoDetect")
    var dapDriverAutoDetect: Boolean = true

    // Launch configuration for debugging (JSON format)
    @OptionTag(tag = "launchConfiguration")
    var launchConfiguration: String = getDefaultLaunchConfigJson()

    // the run command line
    val runCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("run")
            if (runTarget == "all") {
                parameters.add("-a")
            } else if (runTarget != "" && runTarget != "default") {
                parameters.add(runTarget)
            }
            if (runArguments != "") {
                parameters.addAll(ParametersListUtil.parse(runArguments))
            }

            // make command line
            return project.xmakeConfiguration
                .makeCommandLine(parameters, runEnvironment)
                .withWorkDirectory(resolvedWorkingDirectory)
                .withCharset(Charsets.UTF_8)
        }

    // save configuration
    override fun writeExternal(element: Element) {
        super.writeExternal(element)

        XmlSerializer.serializeInto(this, element)
        runEnvironment.writeExternal(element)
    }

    // load configuration
    override fun readExternal(element: Element) {
        super.readExternal(element)

        XmlSerializer.deserializeInto(this, element)
        runEnvironment = EnvironmentVariablesData.readExternal(element)
        runToolkit = runToolkit?.let { toolkit ->
            ToolkitManager.getInstance().findRegisteredToolkitById(toolkit.id)
        }
        // Todo: Optimize to avoid probing delay.
        XMakeInfoManager.getInstance(project).probeXMakeInfo(runToolkit)
    }

    override fun checkConfiguration() {
        if (runToolkit == null) {
            throw RuntimeConfigurationError("Xmake toolkit is not set!")
        }

        if (runWorkingDir.isBlank()) {
            throw RuntimeConfigurationError("Working directory is not set!")
        }

        if (runToolkit?.host?.type == ToolkitHostType.LOCAL) {
            val resolvedWorkingDirectory = try {
                WorkingDirectoryResolver.resolve(project, runWorkingDir, validation = true)
            } catch (e: IncorrectOperationException) {
                throw RuntimeConfigurationError(e.message ?: "Working directory contains invalid macros")
            }
            val workingDirectory = try {
                Path.of(resolvedWorkingDirectory)
            } catch (_: InvalidPathException) {
                throw RuntimeConfigurationError("Working directory is invalid: $resolvedWorkingDirectory")
            }
            if (!Files.isDirectory(workingDirectory)) {
                throw RuntimeConfigurationError("Working directory does not exist: $resolvedWorkingDirectory")
            }
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        XMakeRunConfigurationEditor(project, this)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {

        // save all files
        FileDocumentManager.getInstance().saveAllDocuments()

        if (executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
            return object : CommandLineState(environment) {
                override fun startProcess(): ProcessHandler {
                    return NopProcessHandler()
                }
            }
        }

        project.xmakeConsoleService.whenReady { console ->
            // clear console first
            console.clear()

            // configure and run it
            val xmakeConfiguration = project.xmakeConfiguration
            if (xmakeConfiguration.changed) {
                SystemUtils.runvInConsole(project, console, xmakeConfiguration.configurationCommandLine)
                    ?.addProcessListener(object : ProcessListener {
                        override fun processTerminated(e: ProcessEvent) {
                            SystemUtils.runvInConsole(project, console, runCommandLine, false, true, true)
                        }
                    })
                xmakeConfiguration.changed = false
            } else {
                SystemUtils.runvInConsole(project, console, runCommandLine, true, true, true)
            }
        }

        // does not use builtin run console panel
        return null
    }

    val platforms: Array<String>
        get() = if (project.xmakeInfo.platforms.isNotEmpty()) {
            project.xmakeInfo.platforms.plus("default").toTypedArray()
        } else {
            project.xmakeInfo.architectures.keys.plus("default").toTypedArray()
        }

    val toolchains: Array<String>
        get() = project.xmakeInfo.toolchains.keys.plus("default").toTypedArray()

    val modes: Array<String>
        get() = if (project.xmakeInfo.buildModes.isNotEmpty()) {
            project.xmakeInfo.buildModes.map { it.removePrefix("mode.") }.toTypedArray()
        } else {
            arrayOf("release", "debug")
        }

    fun getArchitecturesByPlatform(platform: String): Array<String> {
        return (project.xmakeInfo.architectures[platform]?.toTypedArray() ?: arrayOf("default"))
    }

    // Get effective DAP driver path
    fun getEffectiveDapDriverPath(): String {
        return if (dapDriverAutoDetect || dapDriverPath.isBlank()) {
            val bestDriver = DapDriverDetector.findBestDriver()
            bestDriver?.path ?: ""
        } else {
            dapDriverPath
        }
    }

    // Get available DAP drivers for UI
    fun getAvailableDapDrivers(): List<DapDriverDetector.DapDriverInfo> {
        return DapDriverDetector.findAvailableDrivers()
    }

    // Validate DAP driver path
    fun validateDapDriverPath(path: String): Boolean {
        return DapDriverDetector.validateDriverPath(path) != null
    }

    companion object {
        
        fun getDefaultGdbLaunchConfigJson(): String {
            return """{
    "stopOnEntry": true,
    "sourceMap": {
        "enabled": "true"
    },
    "showDisassembly": "auto",
    "setupCommands": [
        {
            "description": "Enable pretty-printing for gdb",
            "text": "-enable-pretty-printing",
            "ignoreFailures": true
        }
    ],
    "variables": {
        "showArguments": true,
        "showLocals": true,
        "showGlobals": true,
        "showStatics": true,
        "showRegisters": true
    },
    "ignoreFunctionBpoints": false,
    "stopAtConnectTime": false
}"""
        }

        fun getDefaultLldbLaunchConfigJson(): String {
            return """{
    "stopOnEntry": true,
    "sourceMap": {
        "enabled": "true"
    },
    "showDisassembly": "auto",
    "initCommands": [],
    "preRunTask": {
        "commands": []
    },
    "variables": {
        "showArguments": true,
        "showLocals": true,
        "showGlobals": true,
        "showStatics": true,
        "showRegisters": true
    },
    "ignoreFunctionBpoints": false,
    "stopAtConnectTime": false
}"""
        }

        fun getDefaultLaunchConfigJson(): String {
            return getDefaultLldbLaunchConfigJson()
        }
    }
}
