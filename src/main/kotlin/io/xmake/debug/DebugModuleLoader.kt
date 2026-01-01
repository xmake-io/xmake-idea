package io.xmake.debug

import com.intellij.openapi.project.Project
import com.intellij.openapi.extensions.PluginId
import com.intellij.ide.plugins.PluginManagerCore
import io.xmake.utils.Logger
import io.xmake.utils.SystemUtils
import java.io.File
import java.net.URLClassLoader
import java.lang.reflect.Method

/**
 * Dynamic debug module loader for CLion-specific debugging functionality
 */
object DebugModuleLoader {
    
    private const val TAG = "DebugModuleLoader"
    private const val DEBUG_JAR_NAME = "xmake-clion-debug-1.0.0-base.jar"
    private const val CLION_PLUGIN_ID = "com.intellij.cidr.lang"
    
    private var debugClassLoader: URLClassLoader? = null
    private var debugModuleClass: Class<*>? = null
    private var isLoaded = false
    
    /**
     * Check if CLion is available and load debug module if needed
     */
    fun loadDebugModuleIfNeeded(project: Project): Boolean {
        if (isLoaded) {
            return true
        }
        
        if (!isClionAvailable()) {
            Logger.d(TAG, "CLion not available, skipping debug module loading")
            return false
        }
        
        return try {
            loadDebugModule()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load debug module", e)
            false
        }
    }
    
    /**
     * Check if CLion is available
     */
    private fun isClionAvailable(): Boolean {
        return try {
            val pluginManager = PluginManagerCore
            val clionPlugin = pluginManager.findPlugin(PluginId.getId(CLION_PLUGIN_ID))
            val isAvailable = clionPlugin != null && clionPlugin.isEnabled
            
            // Also check if we're running in CLion by checking IDE name
            val application = com.intellij.openapi.application.ApplicationManager.getApplication()
            if (application == null) {
                Logger.w(TAG, "ApplicationManager.getApplication() returned null")
                return false
            }
            
            val applicationInfo = com.intellij.openapi.application.ApplicationInfo.getInstance()
            val isClionIDE = applicationInfo.build.toString().contains("CL-")
            
            Logger.d(TAG, "IDE check: build=${applicationInfo.build}, isClionIDE=$isClionIDE")
            Logger.d(TAG, "CLion availability check: plugin=${clionPlugin != null}, enabled=${clionPlugin?.isEnabled}, available=$isAvailable")
            
            if (!isAvailable && !isClionIDE) {
                Logger.i(TAG, "Running in non-CLion IDE, CLion debug module will not be loaded")
            }
            
            isAvailable || isClionIDE
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to check CLion availability: ${e.message}")
            false
        }
    }
    
    /**
     * Load the debug module JAR
     */
    private fun loadDebugModule(): Boolean {
        return try {
            // Find the debug JAR in resources
            val jarPath = findDebugJar()
            if (jarPath == null) {
                Logger.w(TAG, "Debug JAR not found: $DEBUG_JAR_NAME")
                return false
            }
            
            Logger.d(TAG, "Loading debug module from: $jarPath")
            
            // Create class loader for the JAR
            val jarFile = File(jarPath)
            val jarUrl = jarFile.toURI().toURL()
            debugClassLoader = URLClassLoader(arrayOf(jarUrl), this::class.java.classLoader)
            
            // Load the main debug module class
            debugModuleClass = debugClassLoader?.loadClass("io.xmake.debug.clion.ClionDebugModule")
            
            isLoaded = true
            Logger.d(TAG, "Debug module loaded successfully")
            true
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load debug module", e)
            false
        }
    }
    
    /**
     * Find the debug JAR in the plugin resources
     */
    private fun findDebugJar(): String? {
        return try {
            Logger.d(TAG, "Searching for debug JAR: $DEBUG_JAR_NAME")
            
            // Use the getModulePath method to get the debug module JAR
            val jarPath = SystemUtils.getModulePath(DEBUG_JAR_NAME)
            
            if (jarPath != null) {
                Logger.i(TAG, "Found debug JAR at: $jarPath")
                return jarPath
            } else {
                Logger.e(TAG, "Debug JAR not found: $DEBUG_JAR_NAME")
                return null
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to find debug JAR", e)
            null
        }
    }
    
    /**
     * Find the project root directory by looking for build.gradle.kts
     */
    private fun findProjectRoot(startDir: File): File? {
        var current = startDir
        while (current.parentFile != null) {
            if (File(current, "build.gradle.kts").exists()) {
                return current
            }
            current = current.parentFile
        }
        return null
    }
    
    /**
     * Create a debug configuration using the loaded module
     */
    fun createDebugConfiguration(project: Project, driverPath: String, launchConfig: String): Any? {
        if (!isLoaded || debugModuleClass == null) {
            Logger.w(TAG, "Debug module not loaded")
            return null
        }
        
        return try {
            val createConfigMethod = debugModuleClass?.getMethod(
                "createDebugConfiguration",
                Project::class.java,
                String::class.java,
                String::class.java
            )
            createConfigMethod?.invoke(null, project, driverPath, launchConfig)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create debug configuration", e)
            null
        }
    }
    
    /**
     * Start a debug session using the loaded module
     */
    fun startDebugSession(
        project: Project, 
        driverPath: String, 
        launchConfig: String, 
        targetPath: String,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap()
    ): Boolean {
        if (!isLoaded || debugModuleClass == null) {
            Logger.w(TAG, "Debug module not loaded")
            return false
        }
        
        return try {
            val startSessionMethod = debugModuleClass?.getMethod(
                "startDebugSession",
                Project::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                List::class.java,
                Map::class.java
            )
            val result = startSessionMethod?.invoke(null, project, driverPath, launchConfig, targetPath, args, env)
            result as? Boolean ?: false
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start debug session", e)
            false
        }
    }
    
    /**
     * Check if debugging is available
     */
    fun isDebuggingAvailable(project: Project): Boolean {
        if (!isLoaded || debugModuleClass == null) {
            return false
        }
        
        return try {
            val isAvailableMethod = debugModuleClass?.getMethod("isDebuggingAvailable", Project::class.java)
            val result = isAvailableMethod?.invoke(null, project)
            result as? Boolean ?: false
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to check debugging availability", e)
            false
        }
    }
    
    /**
     * Unload the debug module
     */
    fun unloadDebugModule() {
        try {
            debugClassLoader?.close()
            debugClassLoader = null
            debugModuleClass = null
            isLoaded = false
            Logger.d(TAG, "Debug module unloaded")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to unload debug module", e)
        }
    }
}
