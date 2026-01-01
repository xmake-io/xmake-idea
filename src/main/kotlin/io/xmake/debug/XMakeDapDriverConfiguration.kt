package io.xmake.debug

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import org.jetbrains.annotations.NotNull
import java.io.File
import io.xmake.debug.loader.DebugModuleLoader
import io.xmake.utils.Logger

/**
 * Simplified XMake DAP driver configuration that delegates to CLion-specific debug module
 */
class XMakeDapDriverConfiguration(
    private val project: Project,
    private val driverPath: String,
    private val userLaunchConfig: String = ""
) {
    
    companion object {
        private const val TAG = "XMakeDapDriverConfiguration"
        
        fun getDriverName(driverPath: String): String {
            val driverInfo = DapDriverDetector.validateDriverPath(driverPath)
            return when (driverInfo?.type) {
                DapDriverDetector.DapDriverType.LLDB_DAP -> "lldb-dap"
                DapDriverDetector.DapDriverType.GDB_DAP -> "gdb-dap"
                else -> "lldb-dap" // fallback
            }
        }
    }
    
    private val internalDebugConfiguration: Any? by lazy {
        try {
            if (DebugModuleLoader.loadDebugModuleIfNeeded(project)) {
                DebugModuleLoader.createDebugConfiguration(project, driverPath, userLaunchConfig)
            } else {
                Logger.d(TAG, "Debug module not available, using fallback")
                createFallbackConfiguration()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create debug configuration", e)
            createFallbackConfiguration()
        }
    }
    
    /**
     * Create a fallback configuration for non-CLion IDEs
     */
    private fun createFallbackConfiguration(): Any? {
        return object {
            fun getProject(): Project = project
            fun getAdapterId(): String = getDriverName(driverPath)
            fun isElevated(): Boolean = false
            fun isEmulateTerminal(): Boolean = false
            
            fun createDriverCommandLine(@NotNull driver: DebuggerDriver, @NotNull arch: ArchitectureType) = 
                GeneralCommandLine(getActualDriverPath())
                    .withWorkDirectory(project.basePath)
                    .withEnvironment(EnvironmentUtil.getEnvironmentMap())
            
            fun getDapLaunchOptions(commandLine: GeneralCommandLine): Map<String, Any> = 
                emptyMap()
            
            fun getDapAttachOptions(pid: Int): Map<String, Any> = 
                emptyMap()
            
            fun createDriver(@NotNull handler: DebuggerDriver.Handler, @NotNull architectureType: ArchitectureType): Any? = 
                null
        }
    }
    
    private fun getActualDriverPath(): String {
        return if (File(driverPath).exists()) {
            driverPath
        } else {
            val bestDriver = DapDriverDetector.findBestDriver()
            bestDriver?.path ?: driverPath
        }
    }
    
    /**
     * Get the debug configuration for use with IntelliJ debugging system
     */
    fun getDebugConfiguration(): Any? = internalDebugConfiguration
    
    /**
     * Start a debug session
     */
    fun startDebugSession(targetPath: String): Boolean {
        return try {
            if (DebugModuleLoader.isDebuggingAvailable(project)) {
                DebugModuleLoader.startDebugSession(project, internalDebugConfiguration ?: return false, targetPath)
            } else {
                Logger.d(TAG, "Debug module not available, cannot start debug session")
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start debug session", e)
            false
        }
    }
    
    /**
     * Check if debugging is available
     */
    fun isDebuggingAvailable(): Boolean {
        return DebugModuleLoader.isDebuggingAvailable(project)
    }
}
