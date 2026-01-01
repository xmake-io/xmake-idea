package io.xmake.debug.clion

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver
import com.jetbrains.cidr.ArchitectureType
import org.jetbrains.annotations.NotNull
import java.io.File

/**
 * CLion-specific debug module that handles DAP driver configuration and debug session management
 * This module directly inherits from DapDriverConfiguration and provides concrete implementation
 */
object ClionDebugModule {
    
    /**
     * Check if debugging is available for the given project
     */
    @JvmStatic
    fun isDebuggingAvailable(project: Project): Boolean {
        return try {
            // Check if CLion debugging classes are available
            Class.forName("com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess")
            Class.forName("com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    
    /**
     * Create a debug configuration for CLion
     */
    @JvmStatic
    fun createDebugConfiguration(project: Project, driverPath: String, launchConfig: String): DapDriverConfiguration {
        return XMakeClionDapDriverConfiguration(project, driverPath, launchConfig)
    }
    
    /**
     * Start a debug session using CLion's debugging infrastructure
     */
    @JvmStatic
    fun startDebugSession(project: Project, configuration: DapDriverConfiguration, targetPath: String): Boolean {
        return try {
            if (configuration is XMakeClionDapDriverConfiguration) {
                configuration.startDebugSession(targetPath)
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Start a debug session using CLion's debugging infrastructure (overload for dynamic loading)
     */
    @JvmStatic
    fun startDebugSession(project: Project, configuration: Any, targetPath: String): Boolean {
        return try {
            // Try to cast to the expected type and call the main method
            startDebugSession(project, configuration as DapDriverConfiguration, targetPath)
        } catch (e: Exception) {
            Logger.e("ClionDebugModule", "Failed to start debug session with Any parameter", e)
            false
        }
    }
    
    /**
     * Concrete implementation of DapDriverConfiguration for XMake
     */
    private class XMakeClionDapDriverConfiguration(
        project: Project,
        private val driverPath: String,
        private val userLaunchConfig: String = ""
    ) : DapDriverConfiguration(project, getDriverName(driverPath), false, false) {
        
        override fun createDriverCommandLine(@NotNull driver: DebuggerDriver, @NotNull arch: ArchitectureType): GeneralCommandLine {
            val actualDriverPath = getActualDriverPath()
            
            return GeneralCommandLine(actualDriverPath)
                .withWorkDirectory(project.basePath)
                .withEnvironment(System.getenv())
        }
        
        override fun getDapLaunchOptions(commandLine: GeneralCommandLine): Map<String, Any> {
            // Simple driver name detection without DapDriverDetector
            val driverType = when {
                driverPath.contains("lldb-dap") -> "LLDB_DAP"
                driverPath.contains("gdb-dap") -> "GDB_DAP"
                else -> "LLDB_DAP"
            }
            
            val defaultConfig = when (driverType) {
                "LLDB_DAP" -> {
                    mapOf(
                        "type" to "lldb-dap",
                        "name" to "LLDB DAP Debugger",
                        "request" to "launch",
                        "program" to "",
                        "args" to emptyList<String>(),
                        "cwd" to "\${workspaceFolder}",
                        "environment" to emptyList<String>(),
                        "stopOnEntry" to false,
                        "launchCommands" to emptyList<String>()
                    )
                }
                "GDB_DAP" -> {
                    mapOf(
                        "type" to "gdb-dap",
                        "name" to "GDB DAP Debugger",
                        "request" to "launch",
                        "program" to "",
                        "args" to emptyList<String>(),
                        "cwd" to "\${workspaceFolder}",
                        "environment" to emptyList<String>(),
                        "stopOnEntry" to false,
                        "setupCommands" to emptyList<String>()
                    )
                }
                else -> emptyMap()
            }
            
            val userConfig = if (userLaunchConfig.isNotEmpty()) {
                try {
                    parseUserLaunchConfig(userLaunchConfig)
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
            
            val finalConfig = defaultConfig.toMutableMap()
            finalConfig.putAll(userConfig)
            
            return finalConfig
        }
        
        override fun getDapAttachOptions(pid: Int): Map<String, Any> {
            return emptyMap()
        }
        
        override fun createDriver(@NotNull handler: DebuggerDriver.Handler, @NotNull architectureType: ArchitectureType): DapDriver {
            return super.createDriver(handler, architectureType)
        }
        
        private fun getActualDriverPath(): String {
            return if (File(driverPath).exists()) {
                driverPath
            } else {
                // Simple fallback without DapDriverDetector
                driverPath
            }
        }
        
        /**
         * Start debug session using CLion's infrastructure
         */
        fun startDebugSession(targetPath: String): Boolean {
            return try {
                val commandLine = GeneralCommandLine(targetPath)
                    .withWorkDirectory(project.basePath)
                    .withEnvironment(System.getenv())
                
                // Use reflection to create TrivialRunParameters to avoid direct dependency
                val trivialParamsClass = Class.forName("com.jetbrains.cidr.execution.debugger.TrivialRunParameters")
                val trivialParamsConstructor = trivialParamsClass.getConstructor(
                    Class.forName("com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration"),
                    Class.forName("com.intellij.execution.configurations.GeneralCommandLine"),
                    Class.forName("com.jetbrains.cidr.ArchitectureType")
                )
                
                val archTypeClass = Class.forName("com.jetbrains.cidr.ArchitectureType")
                val unknownField = archTypeClass.getField("UNKNOWN")
                val unknownArch = unknownField.get(null)
                
                val params = trivialParamsConstructor.newInstance(this, commandLine, unknownArch)
                
                // Use reflection to create console builder
                val consoleBuilderFactoryClass = Class.forName("com.intellij.execution.console.TextConsoleBuilderFactory")
                val consoleBuilderFactoryInstance = consoleBuilderFactoryClass.getMethod("getInstance").invoke(null)
                val consoleBuilder = consoleBuilderFactoryClass.getMethod("createBuilder", Project::class.java)
                    .invoke(consoleBuilderFactoryInstance, project)
                
                // Create debug process
                val debugProcessClass = Class.forName("com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess")
                val debugProcessConstructor = debugProcessClass.getConstructor(
                    trivialParamsClass,
                    Class.forName("com.intellij.xdebugger.XDebugSession"),
                    consoleBuilder::class.java
                )
                
                val debugProcess = debugProcessConstructor.newInstance(params, null, consoleBuilder)
                
                // Start the debug process
                val startMethod = debugProcessClass.getMethod("start")
                startMethod.invoke(debugProcess)
                
                true
            } catch (e: Exception) {
                false
            }
        }
        
        companion object {
            fun getDriverName(driverPath: String): String {
                // Simple driver name detection without DapDriverDetector
                return when {
                    driverPath.contains("lldb-dap") -> "lldb-dap"
                    driverPath.contains("gdb-dap") -> "gdb-dap"
                    else -> "lldb-dap"
                }
            }
            
            private fun parseUserLaunchConfig(config: String): Map<String, Any> {
                // Simple JSON-like parser for user configuration
                val result = mutableMapOf<String, Any>()
                
                // Parse key=value pairs separated by commas
                val pairs = config.split(",")
                for (pair in pairs) {
                    val keyValue = pair.split("=", limit = 2)
                    if (keyValue.size == 2) {
                        val key = keyValue[0].trim().removeSurrounding("\"")
                        val value = keyValue[1].trim().removeSurrounding("\"")
                        result[key] = value
                    }
                }
                
                return result
            }
        }
    }
}
