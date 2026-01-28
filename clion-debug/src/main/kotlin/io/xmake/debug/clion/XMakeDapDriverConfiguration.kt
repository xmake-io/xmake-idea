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

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import io.xmake.debug.clion.utils.Logger
import org.jetbrains.annotations.NotNull
import java.io.File
import java.util.concurrent.TimeUnit

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

    private var missingDllNotified = false
    private var driverDiagnosticChecked = false

    override fun createDriverCommandLine(@NotNull driver: DebuggerDriver, @NotNull arch: ArchitectureType): GeneralCommandLine {
        val env = EnvironmentUtil.getEnvironmentMap().toMutableMap()
        if (this.env.isNotEmpty()) {
            env.putAll(this.env)
        }

        val driverDir = File(driverPath).parent

        if (driverDir != null) {
            val pathKey = env.keys.find { it.equals("path", ignoreCase = true) }
                ?: if (SystemInfo.isWindows) "Path" else "PATH"

            val currentPath = env[pathKey] ?: ""

            val newPath = if (currentPath.isBlank()) driverDir else "$driverDir${File.pathSeparator}$currentPath"
            env[pathKey] = newPath
        }

        if (SystemInfo.isWindows) {
            if (!driverDiagnosticChecked) {
                driverDiagnosticChecked = true
                tryRunDriverDiagnostic(driverDir, env)
            }
        }

        val commandLine = GeneralCommandLine(driverPath)
            .withWorkDirectory(project.basePath)
            .withEnvironment(env)

        // Add -i dap flag for GDB driver
        if (driverName == "gdb-dap") {
            commandLine.addParameter("-i")
            commandLine.addParameter("dap")
        }

        return commandLine
    }

    private fun tryRunDriverDiagnostic(driverDir: String?, env: Map<String, String>) {
        val args = listOf("--version")

        try {
            val pb = ProcessBuilder(listOf(driverPath) + args)
            if (driverDir != null) {
                pb.directory(File(driverDir))
            }
            pb.redirectErrorStream(true)

            val pbEnv = pb.environment()
            pbEnv.clear()
            pbEnv.putAll(env)

            val process = pb.start()
            val finished = process.waitFor(1500, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                Logger.w("XMakeDapDriverConfiguration", "diag: timeout running $driverPath ${args.joinToString(" ")}")
                return
            }

            if (process.exitValue() == -1073741515 && !missingDllNotified) {
                missingDllNotified = true
                notifyDriverMissingDllHint()
            }
        } catch (t: Throwable) {
            Logger.w("XMakeDapDriverConfiguration", "diag: failed to run driver $driverPath: ${t.message}")
        }
    }

    private fun notifyDriverMissingDllHint() {
        val message = "Failed to start the DAP driver (0xC0000135). This is likely caused by missing DLL dependencies.<br/><br/>" +
            "Please run the driver manually in a terminal (e.g. ${File(driverPath).name} --version) and install/copy the missing DLLs based on the error output.<br/><br/>" +
            "driver=$driverPath"
        Notifications.Bus.notify(
            Notification("XMake Debug", "DAP driver may be missing DLLs", message, NotificationType.ERROR),
            project
        )
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

        // Merge configurations
        val finalConfig = mutableMapOf<String, Any>()
        finalConfig.putAll(defaultConfig)
        finalConfig.putAll(mergedConfig)

        // Set target program information
        finalConfig["program"] = commandLine.exePath
        finalConfig["cwd"] = commandLine.workDirectory?.path ?: project.basePath ?: ""
        finalConfig["env"] = commandLine.environment
        finalConfig["args"] = commandLine.parametersList.list

        // Apply driver-specific configurations
        applyDriverSpecificConfigurations(finalConfig)

        return finalConfig
    }

    /**
     * Apply driver-specific configurations to enhance debugging experience
     */
    private fun applyDriverSpecificConfigurations(config: MutableMap<String, Any>) {
        when (driverName) {
            "gdb-dap" -> applyGdbDapConfigurations(config)
            "lldb-dap" -> applyLldbDapConfigurations(config)
        }
    }

    /**
     * Apply GDB DAP specific configurations
     */
    private fun applyGdbDapConfigurations(config: MutableMap<String, Any>) {
        // GDB-specific source path mapping
        // This is necessary because GDB often returns relative paths or absolute paths that differ from IDE's view
        val basePath = project.basePath ?: ""
        if (basePath.isNotEmpty()) {
            val autoSourceMap = mapOf(
                basePath to ".",
                "$basePath/src" to "src"
            )

            // Merge with existing sourceMap from user config
            @Suppress("UNCHECKED_CAST")
            val existingSourceMap = config["sourceMap"] as? Map<String, Any>
            val mergedSourceMap = mutableMapOf<String, Any>()

            if (existingSourceMap != null) {
                mergedSourceMap.putAll(existingSourceMap)
            }

            // Apply auto mappings only if not already present
            autoSourceMap.forEach { (k, v) ->
                if (!mergedSourceMap.containsKey(k)) {
                    mergedSourceMap[k] = v
                }
            }

            // Use both keys for compatibility: sourceMap (common), sourceFileMap (GDB specific)
            config["sourceMap"] = mergedSourceMap
            config["sourceFileMap"] = mergedSourceMap
        }
    }

    /**
     * Apply LLDB DAP specific configurations (placeholder for future enhancements)
     */
    private fun applyLldbDapConfigurations(config: MutableMap<String, Any>) {
        // LLDB-specific configurations can be added here if needed
        // Currently using defaults from DefaultDebugConfigurations
    }

    override fun getDapAttachOptions(pid: Int): Map<String, Any> {
        return emptyMap()
    }

    override fun createDriver(@NotNull handler: DebuggerDriver.Handler, @NotNull architectureType: ArchitectureType): DapDriver {
        return super.createDriver(handler, architectureType)
    }
}
