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

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.shared.XMakeProblem
import io.xmake.utils.exception.XMakeToolkitNotSetException
import io.xmake.utils.execute.createLocalProcess
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.runProcessWithHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

object SystemUtils {
    
    private const val TAG = "SystemUtils"
    private const val CLION_PLUGIN_ID = "com.intellij.cidr.lang"

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
        commandLine: GeneralCommandLine,
        showConsole: Boolean = true,
        showProblem: Boolean = false,
        showExitCode: Boolean = false
    ) = runProcessWithHandler(project, commandLine, showConsole, showProblem, showExitCode) {
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
        
        // Try to get plugin path from the installed plugin descriptor.
        try {
            val plugin = PluginManagerCore.getPlugin(PluginId.getId("io.xmake"))
            
            if (plugin != null) {
                val possiblePaths = listOf(
                    File(plugin.pluginPath.toFile(), "classes/$resourceDir/$resourceName"),
                    File(plugin.pluginPath.toFile(), "$resourceDir/$resourceName")
                )
                
                for (path in possiblePaths) {
                    if (path.exists()) {
                        return path.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to get plugin path: ${e.message}")
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
    
    /**
     * Check if CLion is available
     */
    fun isClionAvailable(): Boolean {
        return try {
            // Check if we're running in CLion by checking IDE name
            val applicationInfo = ApplicationInfo.getInstance()
            val isClionIDE = applicationInfo.build.toString().contains("CL-")
            
            if (isClionIDE) {
                Logger.d(TAG, "Running in CLion IDE")
                return true
            }
            
            // Check whether the CLion plugin is enabled in a non-CLion IDE.
            try {
                val clionPluginId = PluginId.getId(CLION_PLUGIN_ID)
                
                if (PluginManagerCore.isPluginInstalled(clionPluginId) && !PluginManagerCore.isDisabled(clionPluginId)) {
                    Logger.d(TAG, "CLion plugin is available and enabled")
                    return true
                }
            } catch (e: Exception) {
                Logger.d(TAG, "Failed to check CLion plugin: ${e.message}")
            }
            
            Logger.i(TAG, "Running in non-CLion IDE, CLion debug module will not be loaded")
            false
        } catch (e: Exception) {
            Logger.d(TAG, "Failed to check CLion availability: ${e.message}")
            false
        }
    }

    // check if xmake project
    fun isXMakeProject(project: Project): Boolean {
        return project.basePath?.let { File(it, "xmake.lua").exists() } == true
    }

    /**
     * Check if native debug functionality is available
     */
    fun isNativeDebugAvailable(project: Project): Boolean {
        return try {
            // Check if CLion-specific classes are available
            try {
                Class.forName("com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess")
                true
            } catch (e: ClassNotFoundException) {
                Logger.d(TAG, "CLion debugging classes are not available")
                false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error checking debug availability", e)
            false
        }
    }
}

val VirtualFile.pathAsPath: Path get() = Paths.get(path)
