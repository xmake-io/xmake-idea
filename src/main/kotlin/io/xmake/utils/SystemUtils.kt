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
 * @file        SystemUtils.kt
 *
 */
package io.xmake.utils

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.application.ApplicationManager
import io.xmake.debug.DebugModuleLoader
import io.xmake.project.console.XMakeConsole
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.shared.XMakeProblem
import io.xmake.utils.exception.XMakeToolkitNotSetException
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.createLocalProcess
import io.xmake.utils.execute.runProcessWithHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

object SystemUtils {
    
    private const val TAG = "SystemUtils"

    // get platform
    fun platform(): String = when {
        SystemInfo.isWindows -> "windows"
        SystemInfo.isMac -> "macosx"
        else -> "linux"
    }

    // parse problems for the given line
    fun parseProblem(info: String): XMakeProblem? {

        if (SystemInfo.isWindows) {

            // gbk => utf8
            val info_utf8 = String(info.toByteArray(), charset("UTF-8"))

            // parse problem info
            val pattern = Pattern.compile("(.*?)\\(([0-9]*)\\): (.*?) .*?: (.*)")
            val matcher = pattern.matcher(info_utf8)
            if (matcher.find()) {
                val file = matcher.group(1)
                val line = matcher.group(2)
                val kind = matcher.group(3)
                val message = matcher.group(4)
                return XMakeProblem(file, line, "0", kind, message)
            }

        } else {

            // parse problem info
            val pattern = Pattern.compile("^(error: )?(.*?):([0-9]*):([0-9]*): (.*?): (.*)\$")
            val matcher = pattern.matcher(info)
            if (matcher.find()) {
                val file = matcher.group(2)
                val line = matcher.group(3)
                val column = matcher.group(4)
                val kind = matcher.group(5)
                val message = matcher.group(6)
                return XMakeProblem(file, line, column, kind, message)
            }
        }
        return null
    }

    fun runvInConsole(
        project: Project,
        console: XMakeConsole,
        commandLine: GeneralCommandLine,
        showConsole: Boolean = true,
        showProblem: Boolean = false,
        showExitCode: Boolean = false
    ) = runProcessWithHandler(project, console, commandLine, showConsole, showProblem, showExitCode) {
        try {
            val activatedToolkit = project.activatedToolkit
            if (activatedToolkit != null) {
                runBlocking(Dispatchers.Default) {
                    commandLine.createProcess(activatedToolkit)
                }
            } else {
                commandLine.createLocalProcess()
            }
        } catch (e: XMakeToolkitNotSetException) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XMake.NotificationGroup")
                .createNotification("Error with Xmake Toolkit", e.message ?: "", NotificationType.ERROR)
                .notify(project)
            throw ProcessNotCreatedException(e.message ?: "", commandLine)
        }
    }

    fun getResourceFilePath(resourceName: String, resourceDir: String = "lib"): String? {
        // 1. Try to get from plugin directory (layout in sandbox or installed plugin)
        
        // Try to get plugin path using reflection to avoid internal API
        try {
            val pluginManagerClass = Class.forName("com.intellij.ide.plugins.PluginManager")
            val getPluginMethod = pluginManagerClass.getMethod("getPlugin", com.intellij.openapi.extensions.PluginId::class.java)
            val pluginIdClass = Class.forName("com.intellij.openapi.extensions.PluginId")
            val getIdMethod = pluginIdClass.getMethod("getId", String::class.java)
            val pluginId = getIdMethod.invoke(null, "io.xmake")
            val plugin = getPluginMethod.invoke(null, pluginId)
            
            if (plugin != null) {
                val pluginPathMethod = plugin.javaClass.getMethod("getPluginPath")
                val pluginPath = pluginPathMethod.invoke(plugin) as java.nio.file.Path
                
                val possiblePaths = listOf(
                    File(pluginPath.toFile(), "classes/$resourceDir/$resourceName"),
                    File(pluginPath.toFile(), "$resourceDir/$resourceName")
                )
                
                for (path in possiblePaths) {
                    if (path.exists()) {
                        return path.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to get plugin path using reflection: ${e.message}")
        }

        // 2. Try to get from resources (classpath)
        val resourcePath = "/$resourceDir/$resourceName"
        val url = SystemUtils::class.java.getResource(resourcePath)
        
        if (url != null) {
            if (url.protocol == "file") {
                try {
                    val file = File(url.toURI())
                    return file.absolutePath
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to get resource path from file URI", e)
                }
            } else if (url.protocol == "jar") {
                // Extract from JAR to temp file
                try {
                    val tempFile = File.createTempFile("xmake_resource_", "_$resourceName")
                    tempFile.deleteOnExit()
                    SystemUtils::class.java.getResourceAsStream(resourcePath)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    return tempFile.absolutePath
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to extract resource from JAR", e)
                }
            }
        }

        // 3. Fallback: try to extract from stream if URL approach failed but stream exists
        try {
            val stream = SystemUtils::class.java.getResourceAsStream(resourcePath)
            if (stream != null) {
                val tempFile = File.createTempFile("xmake_resource_stream_", "_$resourceName")
                tempFile.deleteOnExit()
                stream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return tempFile.absolutePath
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to extract resource from stream", e)
        }

        // 4. Final fallback for local development (direct file access relative to project root)
        val devPath = File("src/main/resources/$resourceDir/$resourceName")
        if (devPath.exists()) {
            return devPath.absolutePath
        }

        return null
    }

    fun getScriptPath(scriptName: String): String? {
        return getResourceFilePath(scriptName, "scripts")
    }
    
    // check if xmake project
    fun isXMakeProject(project: Project): Boolean {
        return project.basePath?.let { File(it, "xmake.lua").exists() } == true
    }

    // Type id of the native "Xmake Executable" run configuration (CLion's own Custom Build
    // Target under the hood, see XMakeExecutableRunConfigurationType in the clion-debug module,
    // which isn't a compile-time dependency of this module).
    private const val XMAKE_EXECUTABLE_CONFIG_TYPE_ID = "io.xmake.XMakeExecutable"

    // check if the currently selected run configuration is a native "Xmake Executable" config;
    // those are built/run/debugged natively by CLion, so the XMake console actions don't apply
    fun isXMakeExecutableConfigSelected(project: Project): Boolean {
        return RunManager.getInstance(project).selectedConfiguration?.type?.id == XMAKE_EXECUTABLE_CONFIG_TYPE_ID
    }

    /**
     * Check if native debug functionality is available
     */
    fun isNativeDebugAvailable(): Boolean {
        return DebugModuleLoader.loadDebugModuleIfNeeded()
    }
}

val VirtualFile.pathAsPath: Path get() = Paths.get(path)
