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
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.application.ApplicationManager
import io.xmake.debug.XMakeDebugSupport
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
    fun parseProblem(info: String, workingDirectory: Path? = null): XMakeProblem? {
        // xmake diagnostics follow one of the common compiler formats below, on any host OS
        parseGccStyleProblem(info, workingDirectory)?.let { return it }
        parseMsvcFamilyProblem(info, workingDirectory)?.let { return it }
        parseArmCcStyleProblem(info, workingDirectory)?.let { return it }
        parseIarProblem(info, workingDirectory)?.let { return it }
        return parseLineOnlyProblem(info, workingDirectory)
    }

    private fun parseGccStyleProblem(info: String, workingDirectory: Path?): XMakeProblem? {
        val pattern = Pattern.compile("^(error: )?(.*?):([0-9]*):([0-9]*): (.*?): (.*)$")
        val matcher = pattern.matcher(info)
        if (!matcher.find()) return null
        return XMakeProblem(
            file = matcher.group(2),
            line = matcher.group(3),
            column = matcher.group(4),
            kind = matcher.group(5),
            message = matcher.group(6),
            workingDirectory = workingDirectory,
        )
    }

    // Matches MSVC, clang-cl, and Keil C51 diagnostics: file(line[,column]): kind [Cxxxx]: message
    private fun parseMsvcFamilyProblem(info: String, workingDirectory: Path?): XMakeProblem? {
        val pattern = Pattern.compile("^(.*?)\\(([0-9]+)(?:,([0-9]+))?\\): (.*?): (.*)$")
        val matcher = pattern.matcher(info)
        if (!matcher.find()) return null
        return XMakeProblem(
            file = matcher.group(1).unquote(),
            line = matcher.group(2),
            column = matcher.group(3) ?: "0",
            kind = matcher.group(4),
            message = matcher.group(5),
            workingDirectory = workingDirectory,
        )
    }

    // Matches ARM Compiler 5 and TI diagnostics: "file", line N: kind: message
    private fun parseArmCcStyleProblem(info: String, workingDirectory: Path?): XMakeProblem? {
        val pattern = Pattern.compile("^\"(.*)\", line ([0-9]+): (.*?): (.*)$")
        val matcher = pattern.matcher(info)
        if (!matcher.find()) return null
        return XMakeProblem(
            file = matcher.group(1),
            line = matcher.group(2),
            column = "0",
            kind = matcher.group(3),
            message = matcher.group(4),
            workingDirectory = workingDirectory,
        )
    }

    // Matches IAR diagnostics that include a file reference: "file",line  Error[PeNNNN]: message
    private fun parseIarProblem(info: String, workingDirectory: Path?): XMakeProblem? {
        val pattern = Pattern.compile("^\"(.*)\",([0-9]+)\\s+(Error|Warning)\\[Pe[0-9]+\\]: (.*)$")
        val matcher = pattern.matcher(info)
        if (!matcher.find()) return null
        return XMakeProblem(
            file = matcher.group(1),
            line = matcher.group(2),
            column = "0",
            kind = matcher.group(3),
            message = matcher.group(4),
            workingDirectory = workingDirectory,
        )
    }

    // Matches line-only formats (TCC, SDCC, ...): file:line: kind [code]: message
    private fun parseLineOnlyProblem(info: String, workingDirectory: Path?): XMakeProblem? {
        val pattern = Pattern.compile("^(.*?):([0-9]+): (error|warning|fatal error|note)(?: [0-9]+)?: (.*)$")
        val matcher = pattern.matcher(info)
        if (!matcher.find()) return null
        return XMakeProblem(
            file = matcher.group(1),
            line = matcher.group(2),
            column = "0",
            kind = matcher.group(3),
            message = matcher.group(4),
            workingDirectory = workingDirectory,
        )
    }

    private fun String.unquote(): String = removePrefix("\"").removeSuffix("\"")
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

        // The URL can be unavailable for some class loader layouts even when the stream exists.
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

        // Local development fallback for resources that have not been copied to the classpath yet.
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

    /**
     * Check if native debug functionality is available
     */
    fun isNativeDebugAvailable(): Boolean {
        return XMakeDebugSupport.isAvailable()
    }
}

val VirtualFile.pathAsPath: Path get() = Paths.get(path)
