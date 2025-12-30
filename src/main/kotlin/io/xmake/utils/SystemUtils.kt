package io.xmake.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.shared.XMakeProblem
import io.xmake.utils.exception.XMakeToolkitNotSetException
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.runProcessWithHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

object SystemUtils {

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
            val activatedToolkit = project.activatedToolkit ?: throw XMakeToolkitNotSetException()
            runBlocking(Dispatchers.Default) {
                commandLine.createProcess(activatedToolkit)
            }
        } catch (e: XMakeToolkitNotSetException) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XMake.NotificationGroup")
                .createNotification("Error with XMake Toolkit", e.message ?: "", NotificationType.ERROR)
                .notify(project)
            throw ProcessNotCreatedException(e.message ?: "", commandLine)
        }
    }

    fun getScriptPath(scriptName: String): String? {
        println("Searching for script: $scriptName")
        
        // 1. Try to get from plugin directory (layout in sandbox or installed plugin)
        val pluginId = PluginId.getId("io.xmake")
        val plugin = PluginManagerCore.getPlugin(pluginId)
        if (plugin != null) {
            val possiblePaths = listOf(
                File(plugin.pluginPath.toFile(), "classes/scripts/$scriptName"),
                File(plugin.pluginPath.toFile(), "scripts/$scriptName"),
                File(plugin.pluginPath.toFile(), "lib/scripts/$scriptName") // Sometimes it might be here
            )
            
            for (file in possiblePaths) {
                println("Checking path: ${file.absolutePath}")
                if (file.exists()) {
                    println("Found at: ${file.absolutePath}")
                    return file.absolutePath
                }
            }
        }

        // 2. Try to get from resources (classpath)
        val resourcePath = "/scripts/$scriptName"
        val url = SystemUtils::class.java.getResource(resourcePath)
        println("Resource URL: $url")
        
        if (url != null) {
            if (url.protocol == "file") {
                try {
                    val file = File(url.toURI())
                    println("Found resource file: ${file.absolutePath}")
                    return file.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (url.protocol == "jar") {
                // Extract from JAR to temp file
                try {
                    val tempFile = File.createTempFile("xmake_script_", "_$scriptName")
                    tempFile.deleteOnExit()
                    SystemUtils::class.java.getResourceAsStream(resourcePath)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    println("Extracted to temp file: ${tempFile.absolutePath}")
                    return tempFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 3. Fallback: try to extract from stream if URL approach failed but stream exists
        try {
            val stream = SystemUtils::class.java.getResourceAsStream(resourcePath)
            if (stream != null) {
                val tempFile = File.createTempFile("xmake_script_stream_", "_$scriptName")
                tempFile.deleteOnExit()
                stream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                println("Extracted from stream to: ${tempFile.absolutePath}")
                return tempFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 4. Final fallback for local development (direct file access relative to project root)
        val devPath = File("src/main/resources/scripts/$scriptName")
        println("Checking dev path: ${devPath.absolutePath}")
        if (devPath.exists()) {
            return devPath.absolutePath
        }

        println("Script not found: $scriptName")
        return null
    }
}

val VirtualFile.pathAsPath: Path get() = Paths.get(path)
