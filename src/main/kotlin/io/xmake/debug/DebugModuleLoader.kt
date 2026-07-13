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

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import io.xmake.utils.Logger

/**
 * Dynamic bridge to the optional CLion debug module.
 */
object DebugModuleLoader {

    private const val TAG = "DebugModuleLoader"
    private const val DEBUG_CONTENT_MODULE_NAME = "xmake-idea.clion-debug"
    private const val DEBUG_MODULE_CLASS_NAME = "io.xmake.debug.clion.ClionDebugModule"

    private val debugModuleClass: Class<*>? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        try {
            loadDebugModule()
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to load debug module", e)
            null
        } catch (e: LinkageError) {
            Logger.e(TAG, "Debug module is not compatible with this IDE", e)
            null
        }
    }

    /**
     * Resolve and cache the optional debug module if it is available.
     */
    fun loadDebugModuleIfNeeded(): Boolean {
        return debugModuleClass != null
    }

    /**
     * Load the debug module class from the packaged content module.
     */
    private fun loadDebugModule(): Class<*>? {
        val moduleClass = loadDebugModuleFromContentModule()
        if (moduleClass == null) {
            Logger.w(TAG, "Debug module class not found: $DEBUG_MODULE_CLASS_NAME")
            return null
        }

        Logger.d(TAG, "Debug module loaded successfully")
        return moduleClass
    }

    private fun resolveDebugModuleClass(classLoader: ClassLoader): Class<*>? {
        return try {
            Class.forName(DEBUG_MODULE_CLASS_NAME, false, classLoader)
        } catch (_: ClassNotFoundException) {
            null
        } catch (e: LinkageError) {
            Logger.d(TAG, "Debug module class is not linkable from classpath: ${e.message}")
            null
        }
    }

    private fun loadDebugModuleFromContentModule(): Class<*>? {
        val rootDescriptor = (DebugModuleLoader::class.java.classLoader as? PluginAwareClassLoader)
            ?.pluginDescriptor
        if (rootDescriptor == null) {
            Logger.d(TAG, "Root plugin descriptor not found")
            return null
        }

        val classLoader = ContentModuleClassLoaderResolver.resolve(rootDescriptor, DEBUG_CONTENT_MODULE_NAME)
        if (classLoader == null) {
            Logger.d(TAG, "Content module class loader not found: $DEBUG_CONTENT_MODULE_NAME")
            return null
        }

        return resolveDebugModuleClass(classLoader)
    }

    /**
     * Create a debug process using the loaded CLion module.
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
        val moduleClass = debugModuleClass
        if (moduleClass == null) {
            Logger.w(TAG, "Debug module not loaded")
            return null
        }

        return try {
            val createProcessMethod = moduleClass.getMethod(
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
            val result = createProcessMethod.invoke(
                null,
                project,
                driverName,
                driverPath,
                launchConfig,
                targetPath,
                workingDir,
                session,
                args,
                env
            )
            result as? XDebugProcess
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create debug process", e)
            null
        } catch (e: LinkageError) {
            Logger.e(TAG, "Debug module is not compatible with this IDE", e)
            null
        }
    }

}
