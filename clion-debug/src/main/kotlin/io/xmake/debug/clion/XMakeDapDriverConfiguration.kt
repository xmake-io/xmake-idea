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
 * @file        XMakeDapDriverConfiguration.kt
 *
 */
package io.xmake.debug.clion

import io.xmake.debug.clion.utils.Logger
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import org.jetbrains.annotations.NotNull
import java.io.File
import java.util.*

/**
 * XMake DAP driver configuration for CLion debugging
 * Concrete implementation of DapDriverConfiguration for XMake
 */
class XMakeDapDriverConfiguration(
    project: Project,
    private val driverPath: String,
    private val driverName: String,
    private val userLaunchConfig: String = "",
    private val args: List<String> = emptyList(),
    private val env: Map<String, String> = emptyMap()
) : DapDriverConfiguration(project, driverName, false, false) {
    
    companion object {
        private const val TAG = "XMakeDapDriverConfig"
    }

    override fun createDriverCommandLine(@NotNull driver: DebuggerDriver, @NotNull arch: ArchitectureType): GeneralCommandLine {
        val commandLine = GeneralCommandLine(driverPath)
            .withWorkDirectory(project.basePath)
            .withEnvironment(EnvironmentUtil.getEnvironmentMap())
        
        // Add -i dap flag for GDB driver
        if (driverName == "gdb-dap") {
            commandLine.addParameter("-i")
            commandLine.addParameter("dap")
        }
        
        return commandLine
    }

    override fun getDapLaunchOptions(commandLine: GeneralCommandLine): Map<String, Any> {
        // Get default configuration based on driver name
        val defaultConfig = when (driverName) {
            "gdb-dap" -> DefaultDebugConfigurations.getDefaultConfigForDriver("gdb-dap")
            else -> DefaultDebugConfigurations.getDefaultConfigForDriver("lldb-dap")
        }
        
        // Get user configuration from run configuration
        val userConfigJson = userLaunchConfig
        val mergedConfig = DefaultDebugConfigurations.parseLaunchConfig(userConfigJson)
        
        // Build final configuration
        val finalConfig = mutableMapOf<String, Any>()
        finalConfig.putAll(defaultConfig)
        finalConfig.putAll(mergedConfig)
        
        // Override with runtime values
        finalConfig["program"] = commandLine.exePath
        finalConfig["cwd"] = commandLine.workDirectory?.path ?: project.basePath ?: ""
        finalConfig["env"] = commandLine.environment
        finalConfig["args"] = commandLine.parametersList.list
        
        return finalConfig
    }

    override fun getDapAttachOptions(pid: Int): Map<String, Any> {
        return emptyMap()
    }

    override fun createDriver(@NotNull handler: DebuggerDriver.Handler, @NotNull architectureType: ArchitectureType): DapDriver {
        return super.createDriver(handler, architectureType)
    }
}