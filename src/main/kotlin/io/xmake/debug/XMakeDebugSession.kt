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
 * @file        XMakeDebugSession.kt
 *
 */
package io.xmake.debug

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import io.xmake.run.XMakeRunConfiguration
import io.xmake.shared.xmakeConfiguration
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.utils.SystemUtils
import io.xmake.debug.DebugModuleLoader
import io.xmake.utils.execute.runProcess
import io.xmake.utils.Logger
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Manages XMake debugging session creation and lifecycle
 */
class XMakeDebugSession(private val state: RunProfileState, private val environment: ExecutionEnvironment) {
    
    private val project: Project = environment.project
    private val configuration: XMakeRunConfiguration = environment.runProfile as XMakeRunConfiguration
    
    companion object {
        private const val TAG = "XMakeDebugSession"
    }
    
    /**
     * Start a complete debugging session with build, mode check, and debug process
     */
    fun startDebugSession(): com.intellij.execution.ui.RunContentDescriptor? {
        Logger.i(TAG, "Logging mode: ${Logger.getLoggingMode()}")
        Logger.d(TAG, "Starting debug session for target: ${configuration.runTarget}")
        
        // Check if build mode supports debugging symbols first
        checkDebugModeAndPrompt()
        
        // Check if target executable exists
        checkTargetExecutableExists()
        
        // Create and start debug session
        Logger.d(TAG, "Creating debug session...")
        val result = createDebugSession()
        Logger.i(TAG, "Debug session started successfully")
        return result
    }
    
    /**
     * Check if build mode supports debugging symbols and show notification if needed
     */
    private fun checkDebugModeAndPrompt() {
        val debugModes = setOf("debug", "releasedbg")
        
        Logger.v(TAG, "Checking build mode: ${configuration.runMode}")
        
        if (configuration.runMode !in debugModes) {
            Logger.w(TAG, "Build mode '${configuration.runMode}' may not contain debug symbols")
            // Show notification in the bottom right corner instead of blocking dialog
            val notification = Notification(
                "XMake Debug Mode",
                "Build Mode Warning",
                "The current build mode '<b>${configuration.runMode}</b>' may not contain debug symbols.<br/>" +
                "For better debugging experience, consider using 'debug' or 'releasedbg' mode.<br/>" +
                "You can continue debugging, but some debugging features may be limited.",
                NotificationType.WARNING
            )
            
            Notifications.Bus.notify(notification, project)
        } else {
            Logger.v(TAG, "Build mode '${configuration.runMode}' is suitable for debugging")
        }
    }
    
    /**
     * Check if target executable exists and prompt user to build if needed
     */
    private fun checkTargetExecutableExists() {
        val targetName = configuration.runTarget
        val targetPath = getTargetExecutable(project, targetName)
        
        Logger.v(TAG, "Checking target executable for: $targetName")
        
        if (targetPath.isNullOrBlank()) {
            Logger.e(TAG, "Could not find target executable path for $targetName")
            showBuildRequiredNotification(targetName, "Could not determine target executable path")
            throw Exception("Target executable not found for $targetName. Please build the project first.")
        }
        
        val targetFile = File(targetPath)
        if (!targetFile.exists()) {
            Logger.e(TAG, "Target executable not found: $targetPath")
            showBuildRequiredNotification(targetName, "Target executable not found: $targetPath")
            throw Exception("Target executable not found: $targetPath. Please build the project first.")
        }
        
        if (!targetFile.isFile) {
            Logger.e(TAG, "Target path is not a file: $targetPath")
            showBuildRequiredNotification(targetName, "Target path is not a valid file: $targetPath")
            throw Exception("Target path is not a valid file: $targetPath. Please build the project first.")
        }
        
        Logger.v(TAG, "Target executable found and valid: $targetPath")
    }
    
    /**
     * Show notification to user that build is required
     */
    private fun showBuildRequiredNotification(targetName: String, reason: String) {
        val notification = Notification(
            "XMake Debug",
            "Build Required",
            "Target '<b>$targetName</b>' needs to be built before debugging.<br/><br/>" +
            "Reason: $reason<br/><br/>" +
            "Please build the project first using:<br/>" +
            "• Xmake menu → Build Project<br/>" +
            "• Right-click target → Build<br/>" +
            "• Ctrl+F9 (Build Project)",
            NotificationType.ERROR
        )
        Notifications.Bus.notify(notification, project)
    }
    
    /**
     * Create and start the debug session
     */
    private fun createDebugSession(): com.intellij.execution.ui.RunContentDescriptor? {
        return XDebuggerManager.getInstance(project).startSession(environment, object : XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess {
                val targetName = configuration.runTarget
                Logger.d(TAG, "Starting debug process for target: $targetName")
                
                val targetPath = getTargetExecutable(project, targetName)
                if (targetPath.isNullOrBlank()) {
                    Logger.e(TAG, "Could not find target executable for $targetName")
                    throw Exception("Could not find target executable for $targetName")
                }
                
                Logger.v(TAG, "Target executable path: $targetPath")
                
                val targetFile = File(targetPath)
                if (!targetFile.exists() || !targetFile.isFile) {
                    Logger.e(TAG, "Target executable not found or invalid: $targetPath")
                    throw Exception("Target executable not found or invalid: $targetPath")
                }

                // Configure DAP driver
                val dapDriverPath = configuration.getEffectiveDapDriverPath()
                if (dapDriverPath.isBlank()) {
                    Logger.e(TAG, "No DAP driver found")
                    throw Exception("No DAP driver found. Please install lldb-dap or gdb (with DAP support), or specify a custom path in the debug configuration.")
                }
                
                // Get driver name from DapDriverDetector
                val driverInfo = io.xmake.debug.DapDriverDetector.validateDriverPath(dapDriverPath)
                val driverName = when (driverInfo?.type) {
                    io.xmake.debug.DapDriverDetector.DapDriverType.GDB_DAP -> "gdb-dap"
                    else -> "lldb-dap"
                }
                
                Logger.v(TAG, "Using DAP driver: $dapDriverPath ($driverName)")
                
                // Try to create debug process using the configuration
                val debugProcess = createDebugProcess(targetPath, driverName, dapDriverPath, session)
                if (debugProcess == null) {
                    throw Exception("Failed to create debug process")
                }
                Logger.d(TAG, "Debug process created successfully")
                
                return debugProcess
            }
        }).runContentDescriptor
    }
    
    /**
     * Create a debug process
     */
    private fun createDebugProcess(targetPath: String, driverName: String, driverPath: String, session: XDebugSession): XDebugProcess? {
        return try {
            // Try to use CLion debug module
            if (DebugModuleLoader.loadDebugModuleIfNeeded(project) && DebugModuleLoader.isDebuggingAvailable(project)) {
                val launchConfig = configuration.launchConfiguration
                val args = if (configuration.runArguments.isNotBlank()) {
                    ParametersListUtil.parse(configuration.runArguments)
                } else {
                    emptyList()
                }
                val workingDir = configuration.runWorkingDir ?: project.basePath ?: ""
                val debugProcess = DebugModuleLoader.createDebugProcess(
                    project, driverName, driverPath, launchConfig, targetPath, workingDir, session,
                    args, configuration.runEnvironment.envs
                )
                return debugProcess
            }
            
            Logger.w(TAG, "Failed to create debug process using CLion module")
            notifyDebugFailure(project)
            null
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create debug process", e)
            null
        }
    }

    private fun notifyDebugFailure(project: Project) {
        // Notify failure
        NotificationGroupManager.getInstance()
            .getNotificationGroup("XMake.NotificationGroup")
            .createNotification(
                "XMake Debug Support",
                "Failed to load XMake debug module. Debugging features will be unavailable.",
                NotificationType.ERROR
            )
            .notify(project)

        // Check version compatibility
        val appInfo = ApplicationInfo.getInstance()
        val build = appInfo.build
        // CLion 2025.3 corresponds to baseline version 253
        if (build.productCode == "CL" && build.baselineVersion < 253) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XMake.NotificationGroup")
                .createNotification(
                    "XMake Debug Support",
                    "Debugging is only supported in CLion 2025.3 or later. You are using ${appInfo.fullVersion}.",
                    NotificationType.WARNING
                )
                .notify(project)
        }
    }
    
    /**
     * Get the target executable path for debugging
     */
    private fun getTargetExecutable(project: Project, targetName: String): String? {
        Logger.v(TAG, "Getting executable path for target: $targetName")
        val configuration = project.xmakeConfiguration
        val toolkit = project.activatedToolkit ?: return null
        val targetPathScript = SystemUtils.getScriptPath("targetpath.lua")
        if (targetPathScript == null) {
            Logger.e(TAG, "targetpath.lua script not found")
            return null
        }

        val parameters = mutableListOf("l", targetPathScript)
        if (targetName != "default" && targetName.isNotEmpty()) {
            parameters.add(targetName)
        }

        val commandLine = configuration.makeCommandLine(
            parameters,
            EnvironmentVariablesData.DEFAULT
        ).apply {
            withWorkDirectory(project.basePath)
            withEnvironment("XMAKE_SKIP_HISTORY", "1")
            withEnvironment("XMAKE_ROOT", "y")
            withEnvironment("XMAKE_COLOR_TERM", "nocolor")
        }

        return runBlocking {
            val process = commandLine.createProcess()
            val (result, _) = runProcess(process)
            val output = result.getOrNull()?.trim() ?: return@runBlocking null
            
            // parse output with tag __begin__ ... __end__
            val regex = "__begin__([\\s\\S]*?)__end__".toRegex()
            val matchResult = regex.find(output)
            val path = matchResult?.groupValues?.get(1)?.trim()
            
            if (path != null && !File(path).isAbsolute) {
                return@runBlocking File(project.basePath, path).absolutePath
            }
            path
        }
    }
}
