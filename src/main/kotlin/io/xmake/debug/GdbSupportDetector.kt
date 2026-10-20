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
 * @file        GdbSupportDetector.kt
 *
 */
package io.xmake.debug

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.Version
import java.io.File

internal object GdbSupportDetector {

    private const val DETECTION_TIMEOUT_MS = 1_500L

    /** The cache key includes the binary's timestamp and size, so an upgrade invalidates it. */
    private data class DriverIdentity(val path: String, val lastModified: Long, val size: Long) {
        companion object {
            fun of(path: String): DriverIdentity {
                val file = File(path)
                return DriverIdentity(path, file.lastModified(), file.length())
            }
        }
    }

    private val cache: MutableMap<DriverIdentity, DapSupport> = mutableMapOf()

    @Synchronized
    fun detect(path: String): DapSupport {
        val identity = DriverIdentity.of(path)
        cache[identity]?.let { return it }

        val support = detectUncached(path)
        // A launch failure or a timeout is inconclusive, so it is not cached.
        if (support !is DapSupport.Unverified) {
            cache[identity] = support
        }
        return support
    }

    fun versionOutput(path: String): String = try {
        runProcess(path, listOf("--version")).combinedOutput()
    } catch (error: Throwable) {
        ""
    }

    private fun detectUncached(path: String): DapSupport {
        val version = parseGdbVersion(versionOutput(path))
        if (version != null && version < MIN_DAP_GDB_VERSION) {
            return DapSupport.Unsupported(
                "GDB ${version.toCompactString()} detected; " +
                    "DAP requires GDB ${MIN_DAP_GDB_VERSION.toCompactString()}+."
            )
        }

        // `--version` is answered before the interpreter initializes, so only starting the
        // interpreter shows whether this build carries one.
        val check = try {
            runProcess(path, listOf("-i", "dap", "-batch"))
        } catch (error: Throwable) {
            return DapSupport.Unverified(
                "Failed to launch the DAP interpreter via $path: ${error.message ?: error.javaClass.simpleName}"
            )
        }
        return when {
            check.isTimeout ->
                DapSupport.Unverified("Timed out starting the DAP interpreter via $path -i dap -batch")
            check.exitCode == 0 -> DapSupport.Supported
            else -> DapSupport.Unsupported(interpreterFailure(version, check))
        }
    }

    private fun interpreterFailure(version: Version?, check: ProcessOutput): String {
        val owner = version?.let { " of GDB ${it.toCompactString()}" } ?: ""
        val detail = check.combinedOutput().lineSequence().firstOrNull { it.isNotBlank() }?.take(600)
            ?: "exit=${check.exitCode}"
        return "The DAP interpreter$owner failed to start: $detail."
    }

    /** Synchronous detection helper; must be called off the EDT. [CapturingProcessHandler] reads
     *  both streams concurrently, so a full pipe can never fake a timeout. */
    private fun runProcess(executable: String, args: List<String>): ProcessOutput =
        CapturingProcessHandler(
            ProcessBuilder(listOf(executable) + args).start(),
            Charsets.UTF_8,
            "$executable ${args.joinToString(" ")}",
        ).runProcess(DETECTION_TIMEOUT_MS.toInt(), true)

    private fun ProcessOutput.combinedOutput(): String = (stdout + "\n" + stderr).trim()

    private fun parseGdbVersion(output: String): Version? {
        val firstLine = output.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isBlank()) return null
        val match = Regex("""\b(\d+)\.(\d+)(?:\.\d+)?\b""").find(firstLine) ?: return null
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        return Version(major, minor, 0)
    }
}
