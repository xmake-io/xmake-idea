package io.xmake.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.execution.TrivialRunParameters
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.EnvironmentUtil
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.SystemUtils
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.runProcess
import kotlinx.coroutines.runBlocking
import java.io.File
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.util.execution.ParametersListUtil
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import io.xmake.debug.XMakeDapDriverConfiguration

open class XMakeRunner : XMakeDefaultRunner() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return (executorId == DefaultRunExecutor.EXECUTOR_ID || executorId == DefaultDebugExecutor.EXECUTOR_ID) && profile is XMakeRunConfiguration
    }

    override fun getRunnerId(): String = "XMakeRunner"

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val configuration = environment.runProfile
        if (configuration !is XMakeRunConfiguration) {
            return null
        }

        if (environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
            return startDebugSession(state, environment, configuration)
        }

        return super.doExecute(state, environment)
    }

    private fun startDebugSession(state: RunProfileState, environment: ExecutionEnvironment, configuration: XMakeRunConfiguration): RunContentDescriptor? {
        // Check if build mode supports debugging symbols first
        checkDebugModeAndPrompt(environment.project, configuration.runMode)
        
        // Build the project before debugging
        buildProjectBeforeDebug(environment.project, configuration)
        
        return createDebugSession(state, environment, configuration)
    }

    private fun createDebugSession(state: RunProfileState, environment: ExecutionEnvironment, configuration: XMakeRunConfiguration): RunContentDescriptor? {
        return XDebuggerManager.getInstance(environment.project).startSession(environment, object : XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess {
                val targetName = configuration.runTarget
                val targetPath = getTargetExecutable(environment.project, targetName)
                if (targetPath.isNullOrBlank()) {
                    throw Exception("Could not find target executable for $targetName")
                }
                
                val targetFile = File(targetPath)
                if (!targetFile.exists() || !targetFile.isFile) {
                    throw Exception("Target executable not found or invalid: $targetPath")
                }

                // Configure DAP driver
                val dapDriverPath = configuration.getEffectiveDapDriverPath()
                if (dapDriverPath.isBlank()) {
                    throw Exception("No DAP driver found. Please install lldb-dap or gdb-dap, or specify a custom path in the debug configuration.")
                }
                
                val driverConfig = XMakeDapDriverConfiguration(environment.project, dapDriverPath)

                val commandLine = GeneralCommandLine(targetPath)
                    .withWorkDirectory(configuration.runWorkingDir)
                    .withEnvironment(configuration.runCommandLine.environment)

                if (configuration.runArguments.isNotEmpty()) {
                    commandLine.withParameters(ParametersListUtil.parse(configuration.runArguments))
                }

                val params = TrivialRunParameters(
                    driverConfig,
                    commandLine,
                    ArchitectureType.UNKNOWN
                )

                val consoleBuilder = (state as? CommandLineState)?.consoleBuilder 
                    ?: TextConsoleBuilderFactory.getInstance().createBuilder(environment.project)

                val debugProcess = CidrLocalDebugProcess(params, session, consoleBuilder)
                debugProcess.start()
                return debugProcess
            }
        }).runContentDescriptor
    }

    private fun getTargetExecutable(project: Project, targetName: String): String? {
        val configuration = project.xmakeConfiguration
        val toolkit = project.activatedToolkit ?: return null
        val targetPathScript = SystemUtils.getScriptPath("targetpath.lua")
        if (targetPathScript == null) {
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

    private fun buildProjectBeforeDebug(project: Project, configuration: XMakeRunConfiguration) {
        try {
            val toolkit = configuration.runToolkit ?: throw Exception("XMake toolkit is not set")
            
            // Create build command
            val buildCommand = project.xmakeConfiguration
                .makeCommandLine(listOf("build", configuration.runTarget), configuration.runEnvironment)
                .withWorkDirectory(java.io.File(configuration.runWorkingDir))
                .withCharset(java.nio.charset.StandardCharsets.UTF_8)
            
            // Execute build
            runBlocking {
                val process = buildCommand.createProcess(toolkit)
                val (result, _) = runProcess(process)
                
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    throw Exception("Build failed: ${error?.message}")
                }
                
                Log.info("Build completed successfully for target: ${configuration.runTarget}")
            }
        } catch (e: Exception) {
            Log.error("Build failed before debugging", e)
            // Show notification for build failure
            val notification = Notification(
                "XMake Build",
                "Build Failed",
                "Failed to build target '<b>${configuration.runTarget}</b>' before debugging.<br/>" +
                "Error: ${e.message}<br/>" +
                "Please check the build output and fix any errors before debugging.",
                NotificationType.ERROR
            )
            Notifications.Bus.notify(notification, project)
            throw e
        }
    }

    private fun checkDebugModeAndPrompt(project: Project, currentMode: String) {
        val debugModes = setOf("debug", "releasedbg")
        
        if (currentMode !in debugModes) {
            // Show notification in the bottom right corner instead of blocking dialog
            val notification = Notification(
                "XMake Debug Mode",
                "Build Mode Warning",
                "The current build mode '<b>$currentMode</b>' may not contain debug symbols.<br/>" +
                "For better debugging experience, consider using 'debug' or 'releasedbg' mode.<br/>" +
                "You can continue debugging, but some debugging features may be limited.",
                NotificationType.WARNING
            )
            
            Notifications.Bus.notify(notification, project)
        }
    }

    companion object {
        private val Log = Logger.getInstance(XMakeRunner::class.java.name)
    }
}
