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
 * @file        ClionDebugModule.kt
 *
 */
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
        Logger.i(TAG, "Creating debug process: project=${project.name}, driver=$driverName, path=$driverPath, target=$targetPath, workDir=$workingDir, args=$args, env=$env")
        
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

    /**
     * Feed CLion IntelliSense from an xmake-generated compile_commands.json.
     * Returns true if a compilation-database refresh was triggered.
     */
    @JvmStatic
    fun attachCompileCommands(project: Project, compileCommandsPath: String): Boolean {
        if (!CompDBIntegration.isAvailable()) {
            Logger.d(TAG, "Compilation Database subsystem not available")
            return false
        }
        return CompDBIntegration.attachCompileCommands(project, compileCommandsPath)
    }

    /**
     * Register xmake targets as CLion Custom Build Targets (native "Build Target" + gutter markers).
     * [specJson] describes the targets; see [CustomBuildTargetsIntegration]. Returns true on success.
     */
    @JvmStatic
    fun syncBuildTargets(project: Project, specJson: String): Boolean {
        if (!CustomBuildTargetsIntegration.isAvailable()) {
            Logger.d(TAG, "Custom Build Targets subsystem not available")
            return false
        }
        return CustomBuildTargetsIntegration.syncBuildTargets(project, specJson)
    }

    /**
     * Register the native "Xmake Executable" run configuration type (owned by [pluginId]).
     * Idempotent; no-ops when CLion's external run config subsystem is absent.
     */
    @JvmStatic
    fun registerXMakeExecutableType(pluginId: String): Boolean {
        if (!XMakeRunConfigRegistrar.isAvailable()) {
            Logger.d(TAG, "External run configuration subsystem not available")
            return false
        }
        return XMakeRunConfigRegistrar.register(pluginId)
    }
}
