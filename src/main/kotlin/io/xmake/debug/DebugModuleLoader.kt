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
import java.util.jar.JarFile

/**
 * Dynamic debug module loader for CLion-specific debugging functionality
 */
object DebugModuleLoader {
    
    private const val TAG = "DebugModuleLoader"
    private const val DEBUG_MODULE_CLASS_NAME = "io.xmake.debug.clion.ClionDebugModule"
    private const val DEBUG_MODULE_CLASS_ENTRY = "io/xmake/debug/clion/ClionDebugModule.class"
    
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
     * Load the debug module class from the packaged plugin module.
     */
    private fun loadDebugModule(): Boolean {
        return try {
            debugModuleClass = resolveDebugModuleClass()
                ?: loadDebugModuleFromPackagedModule()

            if (debugModuleClass == null) {
                Logger.w(TAG, "Debug module class not found: $DEBUG_MODULE_CLASS_NAME")
                return false
            }

            isLoaded = true
            Logger.d(TAG, "Debug module loaded successfully")
            true
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load debug module", e)
            false
        }
    }
    
    /**
     * Resolve the CLion debug module when it is already visible through the plugin classpath.
     */
    private fun resolveDebugModuleClass(classLoader: ClassLoader? = this::class.java.classLoader): Class<*>? {
        if (classLoader == null) {
            return null
        }
        return try {
            Class.forName(DEBUG_MODULE_CLASS_NAME, false, classLoader)
        } catch (_: ClassNotFoundException) {
            null
        } catch (e: LinkageError) {
            Logger.d(TAG, "Debug module class is not linkable from classpath: ${e.message}")
            null
        }
    }

    private fun loadDebugModuleFromPackagedModule(): Class<*>? {
        val moduleJar = findPackagedDebugModuleJar() ?: return null
        Logger.d(TAG, "Loading debug module from plugin module jar: ${moduleJar.absolutePath}")

        val classLoader = URLClassLoader(arrayOf(moduleJar.toURI().toURL()), this::class.java.classLoader)
        val moduleClass = resolveDebugModuleClass(classLoader)

        if (moduleClass == null) {
            classLoader.close()
        } else {
            debugClassLoader = classLoader
        }

        return moduleClass
    }

    private fun findPackagedDebugModuleJar(): File? {
        val pluginPath = getPluginPath() ?: return null
        val modulesDir = File(pluginPath, "lib/modules")
        if (!modulesDir.isDirectory) {
            return null
        }

        return modulesDir.listFiles { file -> file.isFile && file.extension == "jar" }
            ?.firstOrNull(::containsDebugModuleClass)
    }

    private fun containsDebugModuleClass(jarFile: File): Boolean {
        return try {
            JarFile(jarFile).use { jar -> jar.getEntry(DEBUG_MODULE_CLASS_ENTRY) != null }
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to inspect plugin module jar ${jarFile.absolutePath}: ${e.message}")
            false
        }
    }

    private fun getPluginPath(): File? {
        return try {
            val pluginManagerClass = Class.forName("com.intellij.ide.plugins.PluginManager")
            val getPluginMethod = pluginManagerClass.getMethod("getPlugin", com.intellij.openapi.extensions.PluginId::class.java)
            val pluginIdClass = Class.forName("com.intellij.openapi.extensions.PluginId")
            val getIdMethod = pluginIdClass.getMethod("getId", String::class.java)
            val pluginId = getIdMethod.invoke(null, "io.xmake")
            val plugin = getPluginMethod.invoke(null, pluginId) ?: return null
            val pluginPathMethod = plugin.javaClass.getMethod("getPluginPath")
            val pluginPath = pluginPathMethod.invoke(plugin) as java.nio.file.Path

            pluginPath.toFile()
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to get plugin path: ${e.message}")
            null
        }
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
