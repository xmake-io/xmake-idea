package io.xmake.debug.clion

import io.xmake.debug.clion.utils.Logger
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.filters.TextConsoleBuilderFactory
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
     * Create a debug process using CLion's infrastructure
     */
    @JvmStatic
    fun createDebugProcess(
        project: Project, 
        driverName: String,
        driverPath: String, 
        launchConfig: String, 
        targetPath: String,
        workingDir: String,
        session: XDebugSession,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap()
    ): XDebugProcess {
        Logger.i(TAG, "=== Creating debug process ===")
        Logger.i(TAG, "Project: ${project.name}")
        Logger.i(TAG, "Driver name: $driverName")
        Logger.i(TAG, "Driver path: $driverPath")
        Logger.i(TAG, "Launch config: $launchConfig")
        Logger.i(TAG, "Target path: $targetPath")
        Logger.i(TAG, "Working dir: $workingDir")
        Logger.i(TAG, "Args: $args")
        Logger.i(TAG, "Env: $env")
        
        val configuration = XMakeDapDriverConfiguration(project, driverPath, driverName, launchConfig, args, env)
        
        // Create command line for target executable
        val commandLine = GeneralCommandLine(targetPath)
            .withWorkDirectory(workingDir.ifBlank { project.basePath })
            .withEnvironment(env)
        
        // Create TrivialRunParameters directly using CLion API
        val trivialParams = TrivialRunParameters(configuration, commandLine, ArchitectureType.UNKNOWN)
        
        // Create debug process directly using CLion API
        val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        val debugProcess = CidrLocalDebugProcess(trivialParams, session, consoleBuilder)
        debugProcess.start()
        
        Logger.d(TAG, "Debug process created successfully")
        return debugProcess
    }
}
