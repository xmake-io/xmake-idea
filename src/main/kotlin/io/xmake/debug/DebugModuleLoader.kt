package io.xmake.debug

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugProcess
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
        
        if (!SystemUtils.isClionAvailable()) {
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
     * Load the debug module JAR
     */
    private fun loadDebugModule(): Boolean {
        return try {
            // Find debug JAR in resources
            val jarPath = findDebugJar()
            if (jarPath == null) {
                Logger.w(TAG, "Debug JAR not found: $DEBUG_JAR_NAME")
                return false
            }
            
            Logger.d(TAG, "Loading debug module from: $jarPath")
            
            // Create class loader for JAR
            val jarFile = File(jarPath)
            val jarUrl = jarFile.toURI().toURL()
            debugClassLoader = URLClassLoader(arrayOf(jarUrl), this::class.java.classLoader)
            
            // Load main debug module class
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
     * Find debug JAR in plugin resources
     */
    private fun findDebugJar(): String? {
        return try {
            Logger.d(TAG, "Searching for debug JAR: $DEBUG_JAR_NAME")
            
            // Use getModulePath method to get the debug module JAR
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
     * Find project root directory by looking for build.gradle.kts
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
     * Create a debug process using the loaded module
     */
    fun createDebugProcess(
        project: Project, 
        driverName: String,
        driverPath: String, 
        launchConfig: String, 
        targetPath: String,
        workingDir: String,
        session: XDebugSession,
        args: List<String> = emptyList(),
        env: Map<String, String> = emptyMap()
    ): XDebugProcess? {
        if (!isLoaded || debugModuleClass == null) {
            Logger.w(TAG, "Debug module not loaded")
            return null
        }
        
        return try {
            val createProcessMethod = debugModuleClass?.getMethod(
                "createDebugProcess",
                Project::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                XDebugSession::class.java,
                List::class.java,
                Map::class.java
            )
            val result = createProcessMethod?.invoke(null, project, driverName, driverPath, launchConfig, targetPath, workingDir, session, args, env)
            result as? XDebugProcess
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create debug process", e)
            null
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
     * Unload debug module
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
