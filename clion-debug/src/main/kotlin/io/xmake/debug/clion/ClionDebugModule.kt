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

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.TrivialRunParameters
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import io.xmake.debug.clion.utils.Logger
import java.io.File

/**
 * CLion-specific debug module that handles DAP driver configuration and debug session management
 * This module provides the main interface for CLion debugging functionality
 */
object ClionDebugModule {
    
    private const val TAG = "ClionDebugModule"
    
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
        Logger.i(
            TAG,
            "Creating debug process: project=${project.name}, driver=$driverName, path=$driverPath, " +
                "target=$targetPath, workDir=$workingDir, argumentCount=${args.size}, environmentVariableCount=${env.size}"
        )

        val resolvedWorkingDir = workingDir.takeIf { it.isNotBlank() }
            ?: project.basePath
            ?: File(targetPath).absoluteFile.parent
        val configuration = XMakeDapDriverConfiguration(project, driverPath, driverName, launchConfig, env)

        // Create command line for target executable
        val commandLine = GeneralCommandLine(targetPath)
            .withWorkDirectory(resolvedWorkingDir)
            .withEnvironment(env)
            .withParameters(args)
        
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
