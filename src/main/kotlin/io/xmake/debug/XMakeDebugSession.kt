package io.xmake.debug

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.cidr.execution.TrivialRunParameters
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import io.xmake.run.XMakeRunConfiguration
import io.xmake.shared.xmakeConfiguration
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.utils.SystemUtils
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.runProcess
import io.xmake.utils.XMakeLogger
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
        XMakeLogger.i("XMakeDebugSession", "Logging mode: ${XMakeLogger.getLoggingMode()}")
        XMakeLogger.d(TAG, "Starting debug session for target: ${configuration.runTarget}")
        
        // Check if build mode supports debugging symbols first
        checkDebugModeAndPrompt()
        
        // Check if target executable exists
        checkTargetExecutableExists()
        
        // Create and start debug session
        XMakeLogger.d(TAG, "Creating debug session...")
        val result = createDebugSession()
        XMakeLogger.i(TAG, "Debug session started successfully")
        return result
    }
    
    /**
     * Check if build mode supports debugging symbols and show notification if needed
     */
    private fun checkDebugModeAndPrompt() {
        val debugModes = setOf("debug", "releasedbg")
        
        XMakeLogger.v(TAG, "Checking build mode: ${configuration.runMode}")
        
        if (configuration.runMode !in debugModes) {
            XMakeLogger.w(TAG, "Build mode '${configuration.runMode}' may not contain debug symbols")
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
            XMakeLogger.v(TAG, "Build mode '${configuration.runMode}' is suitable for debugging")
        }
    }
    
    /**
     * Check if target executable exists and prompt user to build if needed
     */
    private fun checkTargetExecutableExists() {
        val targetName = configuration.runTarget
        val targetPath = getTargetExecutable(project, targetName)
        
        XMakeLogger.v(TAG, "Checking target executable for: $targetName")
        
        if (targetPath.isNullOrBlank()) {
            XMakeLogger.e(TAG, "Could not find target executable path for $targetName")
            showBuildRequiredNotification(targetName, "Could not determine target executable path")
            throw Exception("Target executable not found for $targetName. Please build the project first.")
        }
        
        val targetFile = File(targetPath)
        if (!targetFile.exists()) {
            XMakeLogger.e(TAG, "Target executable not found: $targetPath")
            showBuildRequiredNotification(targetName, "Target executable not found: $targetPath")
            throw Exception("Target executable not found: $targetPath. Please build the project first.")
        }
        
        if (!targetFile.isFile) {
            XMakeLogger.e(TAG, "Target path is not a file: $targetPath")
            showBuildRequiredNotification(targetName, "Target path is not a valid file: $targetPath")
            throw Exception("Target path is not a valid file: $targetPath. Please build the project first.")
        }
        
        XMakeLogger.v(TAG, "Target executable found and valid: $targetPath")
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
                XMakeLogger.d(TAG, "Starting debug process for target: $targetName")
                
                val targetPath = getTargetExecutable(project, targetName)
                if (targetPath.isNullOrBlank()) {
                    XMakeLogger.e(TAG, "Could not find target executable for $targetName")
                    throw Exception("Could not find target executable for $targetName")
                }
                
                XMakeLogger.v(TAG, "Target executable path: $targetPath")
                
                val targetFile = File(targetPath)
                if (!targetFile.exists() || !targetFile.isFile) {
                    XMakeLogger.e(TAG, "Target executable not found or invalid: $targetPath")
                    throw Exception("Target executable not found or invalid: $targetPath")
                }

                // Configure DAP driver
                val dapDriverPath = configuration.getEffectiveDapDriverPath()
                if (dapDriverPath.isBlank()) {
                    XMakeLogger.e(TAG, "No DAP driver found")
                    throw Exception("No DAP driver found. Please install lldb-dap or gdb-dap, or specify a custom path in the debug configuration.")
                }
                
                XMakeLogger.v(TAG, "Using DAP driver: $dapDriverPath")
                val driverConfig = XMakeDapDriverConfiguration(project, dapDriverPath, configuration.launchConfiguration)

                val commandLine = GeneralCommandLine(targetPath)
                    .withWorkDirectory(configuration.runWorkingDir)
                    .withEnvironment(configuration.runCommandLine.environment)

                if (configuration.runArguments.isNotEmpty()) {
                    XMakeLogger.v(TAG, "Run arguments: ${configuration.runArguments}")
                    commandLine.withParameters(ParametersListUtil.parse(configuration.runArguments))
                }

                val params = TrivialRunParameters(
                    driverConfig,
                    commandLine,
                    ArchitectureType.UNKNOWN
                )

                val consoleBuilder = (state as? CommandLineState)?.consoleBuilder 
                    ?: TextConsoleBuilderFactory.getInstance().createBuilder(project)

                XMakeLogger.d(TAG, "Creating CidrLocalDebugProcess...")
                val debugProcess = CidrLocalDebugProcess(params, session, consoleBuilder)
                debugProcess.start()
                XMakeLogger.d(TAG, "Debug process started successfully")
                return debugProcess
            }
        }).runContentDescriptor
    }
    
    /**
     * Get the target executable path for debugging
     */
    private fun getTargetExecutable(project: Project, targetName: String): String? {
        XMakeLogger.v(TAG, "Getting executable path for target: $targetName")
        val configuration = project.xmakeConfiguration
        val toolkit = project.activatedToolkit ?: return null
        val targetPathScript = SystemUtils.getScriptPath("targetpath.lua")
        if (targetPathScript == null) {
            XMakeLogger.e(TAG, "targetpath.lua script not found")
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
            val process = commandLine.createProcess(toolkit)
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
