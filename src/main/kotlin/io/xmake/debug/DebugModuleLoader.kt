/*!A Xmake integration in IntelliJ IDEA/Clion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2015-present, Xmake Open Source Community.
 *
 * @author      ruki
 * @file        DebugModuleLoader.kt
 *
 */
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
    private const val DEBUG_JAR_NAME = "xmake-clion-debug.jar"
    
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
     * Feed CLion IntelliSense from an xmake-generated compile_commands.json, loading the CLion
     * module on demand. No-ops (returns false) on IDEA Community / when CLion or the compilation
     * database subsystem is unavailable.
     */
    fun attachCompileCommands(project: Project, compileCommandsPath: String): Boolean {
        if (!loadDebugModuleIfNeeded(project) || debugModuleClass == null) {
            return false
        }
        return try {
            val method = debugModuleClass?.getMethod(
                "attachCompileCommands",
                Project::class.java,
                String::class.java
            )
            val result = method?.invoke(null, project, compileCommandsPath)
            result as? Boolean ?: false
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to attach compile_commands", e)
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
