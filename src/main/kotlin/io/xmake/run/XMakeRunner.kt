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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
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
import io.xmake.debug.XMakeDebugProcess

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
        println("XMakeRunner: startDebugSession called")
        return XDebuggerManager.getInstance(environment.project).startSession(environment, object : XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess {
                println("XDebugProcessStarter.start() called with session: ${session::class.java.simpleName}")
                val targetName = configuration.runTarget
                val targetPath = getTargetExecutable(environment.project, targetName)
                println("targetPath: ${targetPath}")
                if (targetPath.isNullOrBlank()) {
                    throw Exception("Could not find target executable for $targetName")
                }
                
                val targetFile = File(targetPath)
                if (!targetFile.exists() || !targetFile.isFile) {
                    throw Exception("Target executable not found or invalid: $targetPath")
                }

                // Configure lldb-dap driver
                val possibleDriverPaths = listOf(
                    "/usr/local/opt/llvm/bin/lldb-dap",
                    "/opt/homebrew/opt/llvm/bin/lldb-dap",
                    "/usr/bin/lldb-dap",
                    "/usr/local/bin/lldb-dap"
                )
                val driverPath = possibleDriverPaths.find { File(it).exists() } ?: "/usr/local/opt/llvm/bin/lldb-dap"
                
                if (!File(driverPath).exists()) {
                    println("WARNING: lldb-dap not found at any common location, last attempt: $driverPath")
                } else {
                    println("Using lldb-dap at: $driverPath")
                }

                println("Starting debug session for target: $targetName, path: $targetPath")

                val driverConfig = XMakeDapDriverConfiguration(environment.project, driverPath)

                val commandLine = GeneralCommandLine(targetPath)
                    .withWorkDirectory(configuration.runWorkingDir)
                    .withEnvironment(configuration.runCommandLine.environment)

                if (configuration.runArguments.isNotEmpty()) {
                    commandLine.withParameters(ParametersListUtil.parse(configuration.runArguments))
                }

                println("Debug command line: ${commandLine.commandLineString}")

                // For now, use UNKNOWN architecture to avoid compilation issues
                val architecture = ArchitectureType.UNKNOWN
                println("Using architecture: $architecture for target: $targetPath")

                val params = TrivialRunParameters(
                    driverConfig,
                    commandLine,
                    architecture
                )

                val consoleBuilder = (state as? CommandLineState)?.consoleBuilder 
                    ?: TextConsoleBuilderFactory.getInstance().createBuilder(environment.project)

                println("Creating CidrLocalDebugProcess...")
                val debugProcess = XMakeDebugProcess(params, session, consoleBuilder)
                println("XMakeDebugProcess created: ${debugProcess::class.java.simpleName}")
                
                // Manually trigger the debug process start
                try {
                    println("Manually starting debug process...")
                    debugProcess.start()
                    println("Debug process manually started")
                } catch (e: Exception) {
                    println("Failed to manually start debug process: ${e.message}")
                    e.printStackTrace()
                }
                
                return debugProcess
            }
        }).runContentDescriptor
    }

    private fun getTargetExecutable(project: Project, targetName: String): String? {
        println("getTargetExecutable ..")
        val configuration = project.xmakeConfiguration
        val toolkit = project.activatedToolkit ?: return null
        val targetPathScript = SystemUtils.getScriptPath("targetpath.lua")
        if (targetPathScript == null) {
            println("Could not find targetpath.lua script")
            return null
        }
        println("getTargetExecutable: ${targetPathScript}")

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
            println("Target path script output: $output")
            
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

    companion object {
        private val Log = Logger.getInstance(XMakeRunner::class.java.name)
    }
}
