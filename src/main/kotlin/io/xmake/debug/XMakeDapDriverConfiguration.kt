package io.xmake.debug

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.LLValue
import com.jetbrains.cidr.execution.debugger.backend.LLValueData
import com.intellij.openapi.util.Pair
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver

class XMakeDapDriverConfiguration(
    project: Project,
    private val driverPath: String
) : DapDriverConfiguration(project, "XMake Debug", false, false) {

    override fun createDriverCommandLine(driver: DebuggerDriver, arch: ArchitectureType): GeneralCommandLine {
        return GeneralCommandLine(driverPath)
            .withWorkDirectory(project.basePath)
            .withEnvironment(EnvironmentUtil.getEnvironmentMap())
    }

    override fun getDapLaunchOptions(commandLine: GeneralCommandLine): Map<String, Any> {
        return mapOf(
            "program" to commandLine.exePath,
            "cwd" to (commandLine.workDirectory?.path ?: project.basePath ?: ""),
            "env" to (commandLine.environment ?: emptyMap<String, String>()),
            "stopOnEntry" to true,
            "args" to commandLine.parametersList.list
        )
    }

    override fun getDapAttachOptions(pid: Int): Map<String, Any> {
        return emptyMap()
    }

    override fun createDriver(handler: DebuggerDriver.Handler, architectureType: ArchitectureType): DapDriver {
        return XMakeDapDriver(handler, this, architectureType)
    }
}
