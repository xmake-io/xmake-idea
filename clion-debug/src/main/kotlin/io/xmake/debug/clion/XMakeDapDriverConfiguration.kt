package io.xmake.debug.clion

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
    private val userLaunchConfig: String = "",
    private val args: List<String> = emptyList(),
    private val env: Map<String, String> = emptyMap()
) : DapDriverConfiguration(project, getDriverName(driverPath), false, false) {
    
    companion object {
        private const val TAG = "XMakeDapDriverConfig"
        
        fun getDriverName(driverPath: String): String {
            val driverInfo = DapDriverDetector.validateDriverPath(driverPath)
            return when (driverInfo?.type) {
                DapDriverDetector.DapDriverType.LLDB_DAP -> "lldb-dap"
                DapDriverDetector.DapDriverType.GDB_DAP -> "gdb-dap"
                else -> "lldb-dap" // fallback
            }
        }
    }

    override fun createDriverCommandLine(@NotNull driver: DebuggerDriver, @NotNull arch: ArchitectureType): GeneralCommandLine {
        val actualDriverPath = if (File(driverPath).exists()) {
            driverPath
        } else {
            // Use the new detector to find best driver
            val bestDriver = DapDriverDetector.findBestDriver()
            bestDriver?.path ?: driverPath
        }
        
        return GeneralCommandLine(actualDriverPath)
            .withWorkDirectory(project.basePath)
            .withEnvironment(EnvironmentUtil.getEnvironmentMap())
            .withParameters(args)
            .withEnvironment(env)
    }

    override fun getDapLaunchOptions(commandLine: GeneralCommandLine): Map<String, Any> {
        // Get driver type and default configuration
        val driverInfo = DapDriverDetector.validateDriverPath(driverPath)
        val driverType = driverInfo?.type ?: DapDriverDetector.DapDriverType.LLDB_DAP
        val defaultConfig = DefaultDebugConfigurations.getDefaultConfigForDriver(driverType.displayName)
        
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