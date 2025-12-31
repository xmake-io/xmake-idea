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
import java.io.File

class XMakeDapDriverConfiguration(
    project: Project,
    private val driverPath: String
) : DapDriverConfiguration(project, "lldb-dap", false, false) {

    override fun createDriverCommandLine(driver: DebuggerDriver, arch: ArchitectureType): GeneralCommandLine {
        println("XMakeDapDriverConfiguration: createDriverCommandLine START")
        println("XMakeDapDriverConfiguration: createDriverCommandLine with driverPath: $driverPath")
        println("XMakeDapDriverConfiguration: createDriverCommandLine with arch: $arch")
        
        try {
            val actualDriverPath = if (File(driverPath).exists()) {
                println("Using provided driverPath: $driverPath")
                driverPath
            } else {
                // Try to find lldb-dap in common locations
                println("Provided driverPath not found, searching common locations...")
                val commonPaths = listOf(
                    "/usr/local/opt/llvm/bin/lldb-dap",
                    "/opt/homebrew/opt/llvm/bin/lldb-dap",
                    "/usr/bin/lldb-dap",
                    "/usr/local/bin/lldb-dap"
                )
                val foundPath = commonPaths.find { File(it).exists() }
                if (foundPath != null) {
                    println("Found lldb-dap at: $foundPath")
                    foundPath
                } else {
                    println("WARNING: lldb-dap not found in common locations, using: $driverPath")
                    driverPath
                }
            }
            
            println("XMakeDapDriverConfiguration: creating command line with: $actualDriverPath")
            val commandLine = GeneralCommandLine(actualDriverPath)
                .withWorkDirectory(project.basePath)
                .withEnvironment(EnvironmentUtil.getEnvironmentMap())
            
            println("XMakeDapDriverConfiguration: createDriverCommandLine COMPLETED")
            return commandLine
        } catch (e: Exception) {
            println("XMakeDapDriverConfiguration: createDriverCommandLine FAILED: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    override fun getDapLaunchOptions(commandLine: GeneralCommandLine): Map<String, Any> {
        println("XMakeDapDriverConfiguration: getDapLaunchOptions")
        println("  commandLine.exePath: ${commandLine.exePath}")
        println("  commandLine.workDirectory: ${commandLine.workDirectory?.path}")
        println("  commandLine.parameters: ${commandLine.parametersList.list}")
        println("  Is this the target program? ${commandLine.exePath.contains(".dylib") || commandLine.exePath.contains(".so") || commandLine.exePath.contains(".exe")}")
        
        // The commandLine here should be the target program, not the debugger
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
        println("XMakeDapDriverConfiguration: createDriver with architecture: $architectureType")
        try {
            val driver = XMakeDapDriver(handler, this, architectureType)
            println("XMakeDapDriverConfiguration: createDriver completed")
            return driver
        } catch (e: Exception) {
            println("XMakeDapDriverConfiguration: createDriver failed: ${e.message}")
            throw e
        }
    }
}
