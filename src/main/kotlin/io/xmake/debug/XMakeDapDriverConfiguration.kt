package io.xmake.debug

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import org.jetbrains.annotations.NotNull
import java.io.File
import kotlin.collections.mutableMapOf

class XMakeDapDriverConfiguration(
    project: Project,
    private val driverPath: String
) : DapDriverConfiguration(project, getDriverName(driverPath), false, false) {
    
    companion object {
        fun getDriverName(driverPath: String): String {
            val driverInfo = DapDriverDetector.validateDriverPath(driverPath)
            return when (driverInfo?.type) {
                DapDriverDetector.DapDriverType.LLDB_DAP -> "lldb-dap"
                DapDriverDetector.DapDriverType.GDB_DAP -> "gdb-dap"
                else -> "lldb-dap" // fallback
            }
        }
    }

    override fun createDriverCommandLine(@NotNull driver: DebuggerDriver, @NotNull arch: ArchitectureType): GeneralCommandLine {
        val actualDriverPath = if (File(driverPath).exists()) {
            driverPath
        } else {
            // Use the new detector to find best driver
            val bestDriver = DapDriverDetector.findBestDriver()
            bestDriver?.path ?: driverPath
        }
        
        return GeneralCommandLine(actualDriverPath)
            .withWorkDirectory(project.basePath)
            .withEnvironment(EnvironmentUtil.getEnvironmentMap())
    }

    override fun getDapLaunchOptions(commandLine: GeneralCommandLine): Map<String, Any> {
        // Add debug environment variables
        val debugEnv = mutableMapOf<String, String>()
        debugEnv["DYLD_LIBRARY_PATH"] = "/usr/local/opt/llvm/lib"
        debugEnv["LLDB_DEBUGSERVER_PATH"] = "/usr/local/opt/llvm/bin"
        
        return mapOf(
            "program" to commandLine.exePath,
            "cwd" to (commandLine.workDirectory?.path ?: project.basePath ?: ""),
            "env" to debugEnv,
            "stopOnEntry" to false,
            "args" to commandLine.parametersList.list,
            "sourceMap" to mapOf(
                "enabled" to "true"
            )
        )
    }

    override fun getDapAttachOptions(pid: Int): Map<String, Any> {
        return emptyMap()
    }

    override fun createDriver(@NotNull handler: DebuggerDriver.Handler, @NotNull architectureType: ArchitectureType): DapDriver {
        return super.createDriver(handler, architectureType)
    }
}
