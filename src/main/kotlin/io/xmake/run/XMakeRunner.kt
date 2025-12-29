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
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.runProcess
import kotlinx.coroutines.runBlocking
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration

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
        return XDebuggerManager.getInstance(environment.project).startSession(environment, object : XDebugProcessStarter() {
            override fun start(session: XDebugSession): XDebugProcess {
                val targetName = configuration.runTarget
                val targetPath = getTargetExecutable(environment.project, targetName)
                    ?: throw Exception("Could not find target executable for $targetName")

                val driverConfig = XMakeDapDriverConfiguration(environment.project, "/usr/local/opt/llvm/bin/lldb-dap")

                val commandLine = GeneralCommandLine(targetPath)
                    .withWorkDirectory(configuration.runWorkingDir)
                    .withEnvironment(configuration.runCommandLine.environment)
                    .withParameters(configuration.runCommandLine.parametersList.list)

                val params = TrivialRunParameters(
                    driverConfig,
                    commandLine,
                    ArchitectureType.UNKNOWN
                )

                val consoleBuilder = (state as? CommandLineState)?.consoleBuilder 
                    ?: TextConsoleBuilderFactory.getInstance().createBuilder(environment.project)

                return CidrLocalDebugProcess(params, session, consoleBuilder)
            }
        }).runContentDescriptor
    }

    private class XMakeDapDriverConfiguration(
        project: Project,
        private val driverPath: String
    ) : DapDriverConfiguration(project, "lldb-dap", false, false) {

        override fun createDriverCommandLine(driver: DebuggerDriver, arch: ArchitectureType): GeneralCommandLine {
            return GeneralCommandLine(driverPath)
                .withWorkDirectory(project.basePath)
                .withEnvironment(EnvironmentUtil.getEnvironmentMap())
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        }

        override fun getDapLaunchOptions(commandLine: GeneralCommandLine): Map<String, Any> {
            return mapOf(
                "program" to commandLine.exePath,
                "args" to commandLine.parametersList.list,
                "cwd" to (commandLine.workDirectory?.path ?: ""),
                "env" to commandLine.environment
            )
        }

        override fun getDapAttachOptions(pid: Int): Map<String, Any> {
            return emptyMap()
        }
    }

    private fun getTargetExecutable(project: Project, targetName: String): String? {
        val configuration = project.xmakeConfiguration
        val toolkit = project.activatedToolkit ?: return null

        val commandLine = configuration.makeCommandLine(
            listOf("l", "-c", "import(\"core.project.project\"); local target = project.target(\"$targetName\"); if target then print(target:targetfile()) end"),
            EnvironmentVariablesData.DEFAULT
        )

        return runBlocking {
            val process = commandLine.createProcess(toolkit)
            val (result, _) = runProcess(process)
            result.getOrNull()?.trim()
        }
    }

    companion object {
        private val Log = Logger.getInstance(XMakeRunner::class.java.name)
    }
}
