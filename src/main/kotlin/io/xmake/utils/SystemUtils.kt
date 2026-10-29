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

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import io.xmake.shared.XMakeProblem
import java.util.concurrent.ConcurrentHashMap
import java.io.File
import java.nio.file.Path
import java.util.regex.Matcher
import java.util.regex.Pattern

object SystemUtils {
    
    private val Log = logger<SystemUtils>()

    private val extractedResources = ConcurrentHashMap<String, File>()

    private val GCC_STYLE = Pattern.compile("^(?:error: )?(.*?):([0-9]*):([0-9]*): (.*?): (.*)$")
    private val MSVC_FAMILY = Pattern.compile("^(.*?)\\(([0-9]+)(?:,([0-9]+))?\\): (.*?): (.*)$")
    private val ARM_CC_STYLE = Pattern.compile("^\"(.*)\", line ([0-9]+): (.*?): (.*)$")
    private val IAR_STYLE = Pattern.compile("^\"(.*)\",([0-9]+)\\s+(Error|Warning)\\[Pe[0-9]+]: (.*)$")
    private val LINE_ONLY = Pattern.compile("^(.*?):([0-9]+): (error|warning|fatal error|note)(?: [0-9]+)?: (.*)$")

    // get platform
    fun platform(): String = when {
        SystemInfo.isWindows -> "windows"
        SystemInfo.isMac -> "macosx"
        else -> "linux"
    }

    private val problemParsers = listOf(
        ::parseGccStyleProblem,
        ::parseMsvcFamilyProblem,
        ::parseArmCcStyleProblem,
        ::parseIarProblem,
        ::parseLineOnlyProblem,
    )

    fun parseProblem(info: String, workingDirectory: Path? = null): XMakeProblem? =
        problemParsers.firstNotNullOfOrNull { parser -> parser(info, workingDirectory) }

    private fun parseGccStyleProblem(info: String, workingDirectory: Path?): XMakeProblem? {
        val pattern = GCC_STYLE
        val matcher = pattern.matcher(info)
        if (!matcher.find()) return null
        return XMakeProblem(
            file = matcher.group(1),
            line = matcher.intGroup(2),
            column = matcher.intGroup(3),
            kind = matcher.group(4),
            message = matcher.group(5),
            workingDirectory = workingDirectory,
        )
    }

    // Matches MSVC, clang-cl, and Keil C51 diagnostics: file(line[,column]): kind [Cxxxx]: message
    private fun parseMsvcFamilyProblem(info: String, workingDirectory: Path?): XMakeProblem? {
        val pattern = MSVC_FAMILY
        val matcher = pattern.matcher(info)
        if (!matcher.find()) return null
        return XMakeProblem(
            file = matcher.group(1).unquote(),
            line = matcher.intGroup(2),
            column = matcher.intGroup(3),
            kind = matcher.group(4),
            message = matcher.group(5),
            workingDirectory = workingDirectory,
        )
    }

    // Matches ARM Compiler 5 and TI diagnostics: "file", line N: kind: message
    private fun parseArmCcStyleProblem(info: String, workingDirectory: Path?): XMakeProblem? {
        val pattern = ARM_CC_STYLE
        val matcher = pattern.matcher(info)
        if (!matcher.find()) return null
        return XMakeProblem(
            file = matcher.group(1),
            line = matcher.intGroup(2),
            kind = matcher.group(3),
            message = matcher.group(4),
            workingDirectory = workingDirectory,
        )
    }

    // Matches IAR diagnostics that include a file reference: "file",line  Error[PeNNNN]: message
    private fun parseIarProblem(info: String, workingDirectory: Path?): XMakeProblem? {
        val pattern = IAR_STYLE
        val matcher = pattern.matcher(info)
        if (!matcher.find()) return null
        return XMakeProblem(
            file = matcher.group(1),
            line = matcher.intGroup(2),
            kind = matcher.group(3),
            message = matcher.group(4),
            workingDirectory = workingDirectory,
        )
    }

    // Matches line-only formats (TCC, SDCC, ...): file:line: kind [code]: message
    private fun parseLineOnlyProblem(info: String, workingDirectory: Path?): XMakeProblem? {
        val pattern = LINE_ONLY
        val matcher = pattern.matcher(info)
        if (!matcher.find()) return null
        return XMakeProblem(
            file = matcher.group(1),
            line = matcher.intGroup(2),
            kind = matcher.group(3),
            message = matcher.group(4),
            workingDirectory = workingDirectory,
        )
    }

    private fun String.unquote(): String = removePrefix("\"").removeSuffix("\"")

    private fun Matcher.intGroup(index: Int): Int = group(index)?.toIntOrNull() ?: 0

    fun getResourceFilePath(resourceName: String, resourceDir: String): String? {
        val resourcePath = "/$resourceDir/$resourceName"
        val url = SystemUtils::class.java.getResource(resourcePath)
        
        if (url != null) {
            if (url.protocol == "file") {
                try {
                    val file = File(url.toURI())
                    return file.absolutePath
                } catch (e: Exception) {
                    Log.error("Failed to get resource path from file URI", e)
                }
            } else if (url.protocol == "jar") {
                try {
                    val cached = extractedResources[resourcePath]
                    if (cached != null) return cached.absolutePath
                    val tempFile = File.createTempFile("xmake_resource_", "_$resourceName")
                    tempFile.deleteOnExit()
                    SystemUtils::class.java.getResourceAsStream(resourcePath)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    extractedResources[resourcePath] = tempFile
                    return tempFile.absolutePath
                } catch (e: Exception) {
                    Log.error("Failed to extract resource from JAR", e)
                }
            }
        }

        // The URL can be unavailable for some class loader layouts even when the stream exists.
        try {
            val stream = SystemUtils::class.java.getResourceAsStream(resourcePath)
            if (stream != null) {
                val cached = extractedResources[resourcePath]
                if (cached != null) return cached.absolutePath
                val tempFile = File.createTempFile("xmake_resource_stream_", "_$resourceName")
                tempFile.deleteOnExit()
                stream.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                extractedResources[resourcePath] = tempFile
                return tempFile.absolutePath
            }
        } catch (e: Exception) {
            Log.error("Failed to extract resource from stream", e)
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
    
}

