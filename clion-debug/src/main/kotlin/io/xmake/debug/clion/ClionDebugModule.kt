package io.xmake.debug.clion

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.TrivialRunParameters
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.ArchitectureType
import org.jetbrains.annotations.NotNull
import java.io.File
import java.util.*

/**
 * CLion-specific debug module that handles DAP driver configuration and debug session management
 * This module provides the main interface for CLion debugging functionality
 */
object ClionDebugModule {
    
    private const val TAG = "ClionDebugModule"
    
    /**
     * Check if debugging is available for the given project
     */
    @JvmStatic
    fun isDebuggingAvailable(project: Project): Boolean {
        return try {
            // Check if CLion debugging classes are available
            Class.forName("com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration")
            true
        } catch (e: ClassNotFoundException) {
            Logger.d(TAG, "CLion debugging classes not available: ${e.message}")
            false
        }
    }
    
    /**
     * Create a debug configuration using the provided parameters
     */
    @JvmStatic
    fun createDebugConfiguration(project: Project, driverPath: String, launchConfig: String): XMakeDapDriverConfiguration {
        Logger.i(TAG, "Creating debug configuration")
        Logger.d(TAG, "Driver path: $driverPath")
        Logger.d(TAG, "Launch config: $launchConfig")
        
        return XMakeDapDriverConfiguration(project, driverPath, launchConfig)
    }
    
    /**
     * Start a debug session using the provided parameters
     */
    @JvmStatic
    fun startDebugSession(
        project: Project, 
        driverPath: String, 
        launchConfig: String, 
        targetPath: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap()
    ): Boolean {
        Logger.i(TAG, "=== Starting debug session ===")
        Logger.i(TAG, "Project: ${project.name}")
        Logger.i(TAG, "Driver path: $driverPath")
        Logger.i(TAG, "Launch config: $launchConfig")
        Logger.i(TAG, "Target path: $targetPath")
        Logger.i(TAG, "Args: $args")
        Logger.i(TAG, "Env: $env")
        
        return try {
            val configuration = XMakeDapDriverConfiguration(project, driverPath, launchConfig, args, env)
            startDebugSessionInternal(project, configuration, targetPath)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start debug session", e)
            false
        }
    }
    
    /**
     * Internal method to start debug session using CLion's infrastructure
     */
    private fun startDebugSessionInternal(project: Project, configuration: XMakeDapDriverConfiguration, targetPath: String): Boolean {
        return try {
            val commandLine = GeneralCommandLine(configuration.driverPath)
                .withWorkDirectory(project.basePath)
                .withEnvironment(System.getenv())
                .withParameters(configuration.args)
                .withEnvironment(configuration.env)
        
            // Create TrivialRunParameters directly using CLion API
            val trivialParams = TrivialRunParameters(configuration, commandLine, ArchitectureType.SYSTEM_TYPE)
            
            // Create debug process directly using CLion API
            val debugProcess = CidrLocalDebugProcess(
                trivialParams,
                project.xdebugSession as XDebugSession
            )
            
            // Start debug process
            debugProcess.start()
            
            true
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start debug process", e)
            false
        }
    }
}
