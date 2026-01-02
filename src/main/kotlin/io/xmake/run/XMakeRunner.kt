package io.xmake.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import io.xmake.debug.XMakeDebugSession
import io.xmake.utils.Logger
import com.intellij.openapi.project.Project

open class XMakeRunner : XMakeDefaultRunner() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (profile !is XMakeRunConfiguration) {
            return false
        }
        
        // Always allow run executor
        if (executorId == DefaultRunExecutor.EXECUTOR_ID) {
            return true
        }
        
        // Only allow debug executor if native debug is available
        if (executorId == DefaultDebugExecutor.EXECUTOR_ID) {
            return isNativeDebugAvailable(profile.project)
        }
        
        return false
    }

    override fun getRunnerId(): String = "XMakeRunner"

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val configuration = environment.runProfile
        if (configuration !is XMakeRunConfiguration) {
            return null
        }

        if (environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
            // Check if debug is available before starting debug session
            if (!isNativeDebugAvailable(configuration.project)) {
                Logger.w(TAG, "Debug functionality is not available in this IDE. Please use CLion for C/C++ debugging.")
                return null
            }
            return XMakeDebugSession(state, environment).startDebugSession()
        }

        return super.doExecute(state, environment)
    }
    
    /**
     * Check if native debug functionality is available
     */
    private fun isNativeDebugAvailable(project: Project): Boolean {
        return try {
            var pluginAvailable = false
            
            // Check if native debug plugin is available using reflection
            try {
                val pluginManagerClass = Class.forName("com.intellij.ide.plugins.PluginManager")
                val findPluginMethod = pluginManagerClass.getMethod("findPlugin", com.intellij.openapi.extensions.PluginId::class.java)
                val nativeDebugPlugin = findPluginMethod.invoke(null, com.intellij.openapi.extensions.PluginId.getId("com.intellij.nativeDebug"))
                
                if (nativeDebugPlugin != null) {
                    val isEnabledMethod = nativeDebugPlugin.javaClass.getMethod("isEnabled")
                    val isEnabled = isEnabledMethod.invoke(nativeDebugPlugin) as Boolean
                    pluginAvailable = isEnabled
                    
                    if (!isEnabled) {
                        Logger.d(TAG, "Native debug plugin is not enabled")
                    }
                } else {
                    Logger.d(TAG, "Native debug plugin is not available")
                }
            } catch (e: Exception) {
                Logger.d(TAG, "Failed to check native debug plugin: ${e.message}")
            }
            
            // Check if CLion-specific classes are available
            try {
                Class.forName("com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess")
                // Classes are available, return true regardless of plugin status
                true
            } catch (e: ClassNotFoundException) {
                Logger.d(TAG, "CLion debugging classes are not available")
                // Classes not available, return plugin status
                pluginAvailable
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking debug availability", e)
            false
        }
    }

    companion object {
        private const val TAG = "XMakeRunner"
    }
}
