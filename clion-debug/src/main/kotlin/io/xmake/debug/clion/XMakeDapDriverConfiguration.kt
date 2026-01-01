package io.xmake.debug.clion

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver
import com.jetbrains.cidr.ArchitectureType
import org.jetbrains.annotations.NotNull
import java.io.File
import java.util.*

/**
 * XMake DAP driver configuration for CLion debugging
 * Concrete implementation of DapDriverConfiguration for XMake
 */
class XMakeDapDriverConfiguration(
    val project: Project,
    val driverPath: String,
    val userLaunchConfig: String = "",
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
) : DapDriverConfiguration(project, getDriverName(driverPath), false, false) {
    
    companion object {
        private const val TAG = "XMakeDapDriverConfig"
        
        fun getDriverName(driverPath: String): String {
            return DapDriverDetector.detectDriverType(driverPath).displayName
        }
    }
    
    override fun createDriverCommandLine(@NotNull driver: DebuggerDriver, @NotNull arch: ArchitectureType): GeneralCommandLine {
        val actualDriverPath = getActualDriverPath()
        
        return GeneralCommandLine(actualDriverPath)
            .withWorkDirectory(project.basePath)
            .withEnvironment(System.getenv())
            .withParameters(args)
            .withEnvironment(env)
    }
    
    override fun getDapLaunchOptions(commandLine: GeneralCommandLine): Map<String, Any> {
        // Parse user launch configuration
        val launchOptions = parseUserLaunchConfig(userLaunchConfig)
        
        // Merge with default configuration
        return DefaultDebugConfigurations.defaultDapConfig + launchOptions
    }
    
    override fun getDapAttachOptions(pid: Int): Map<String, Any> {
        return mapOf(
            "pid" to pid,
            "attach" to true
        )
    }
    
    override fun createDriver(@NotNull handler: DebuggerDriver.Handler, @NotNull architectureType: ArchitectureType): DapDriver {
        return try {
            // Directly create DAP driver using CLion API
            DapDriver(handler, architectureType)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create DAP driver", e)
            throw RuntimeException("Failed to create DAP driver", e)
        }
    }
    
    private fun getActualDriverPath(): String {
        return if (File(driverPath).exists()) {
            driverPath
        } else {
            // Try to find the driver in common locations
            val bestDriver = DapDriverDetector.findBestDriver()
            bestDriver?.path ?: driverPath
        }
    }
    
    private fun parseUserLaunchConfig(config: String): Map<String, Any> {
        return try {
            DefaultDebugConfigurations.parseLaunchConfig(config)
        } catch (e: Exception) {
            Logger.w(TAG, "Failed to parse launch config: $config", e)
            emptyMap()
        }
    }
}