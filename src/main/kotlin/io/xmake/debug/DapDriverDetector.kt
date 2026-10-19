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
 * @file        DapDriverDetector.kt
 *
 */
package io.xmake.debug

import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.Version
import java.io.File

/**
 * DAP driver path detection utility
 */
object DapDriverDetector {

    /** The cache key includes the binary's timestamp and size, so an upgrade invalidates it. */
    private data class DriverIdentity(val path: String, val lastModified: Long, val size: Long) {
        companion object {
            fun of(path: String): DriverIdentity {
                val file = File(path)
                return DriverIdentity(path, file.lastModified(), file.length())
            }
        }
    }

    private val gdbSupportCache: MutableMap<DriverIdentity, DapSupport> = mutableMapOf()

    /** Synchronous detection helper; must be called off the EDT. [CapturingProcessHandler] reads
     *  both streams concurrently, so a full pipe can never fake a timeout. */
    private fun runProcess(executable: String, args: List<String>): ProcessOutput =
        CapturingProcessHandler(
            ProcessBuilder(listOf(executable) + args).start(),
            Charsets.UTF_8,
            "$executable ${args.joinToString(" ")}",
        ).runProcess(DETECTION_TIMEOUT_MS.toInt(), true)

    private fun versionOutput(path: String): String = try {
        runProcess(path, listOf("--version")).combinedOutput()
    } catch (error: Throwable) {
        ""
    }

    private fun ProcessOutput.combinedOutput(): String = (stdout + "\n" + stderr).trim()

    private fun parseGdbVersion(output: String): Version? {
        val firstLine = output.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isBlank()) return null
        val match = Regex("""\b(\d+)\.(\d+)(?:\.\d+)?\b""").find(firstLine) ?: return null
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        return Version(major, minor, 0)
    }

    private fun truncateDiagnostics(text: String, maxChars: Int = 600): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed
        return trimmed.take(maxChars) + "…"
    }

    @Synchronized
    private fun getGdbDapSupport(path: String): DapSupport {
        val identity = DriverIdentity.of(path)
        gdbSupportCache[identity]?.let { return it }

        val version = parseGdbVersion(versionOutput(path))
        if (version != null) {
            val support = if (version >= MIN_DAP_GDB_VERSION) {
                DapSupport.Supported
            } else {
                DapSupport.Unsupported(version)
            }
            gdbSupportCache[identity] = support
            return support
        }

        val check = try {
            runProcess(path, listOf("-i", "dap", "--version"))
        } catch (error: Throwable) {
            return DapSupport.Unverified(
                "Failed to launch the DAP interpreter via $path: ${error.message ?: error.javaClass.simpleName}"
            )
        }
        val output = truncateDiagnostics(check.combinedOutput())
        val support = when {
            check.isTimeout ->
                DapSupport.Unverified("Timed out starting the DAP interpreter via $path -i dap --version")
            check.exitCode != 0 ->
                DapSupport.Unverified(
                    if (output.isBlank()) {
                        "Failed to start the DAP interpreter via $path -i dap --version, exit=${check.exitCode}"
                    } else {
                        "Failed to start the DAP interpreter, exit=${check.exitCode}: $output"
                    }
                )
            else -> DapSupport.Supported
        }
        // A launch failure or a timeout is inconclusive, so it is not cached.
        if (support !is DapSupport.Unverified) {
            gdbSupportCache[identity] = support
        }
        return support
    }

    /**
     * Detect DAP driver type based on file name and content
     */
    fun detectDriverType(path: String): DapDriverType? {
        val file = File(path)
        if (!file.exists()) {
            return null
        }

        val fileName = file.name.lowercase()
        return when {
            fileName.contains("lldb-dap") -> DapDriverType.LLDB
            fileName.contains("lldb-vscode") -> DapDriverType.LLDB
            fileName.contains("gdb") && !fileName.contains("lldb") -> DapDriverType.GDB
            else -> {
                val output = versionOutput(path).lowercase()
                when {
                    output.contains("lldb-dap") || output.contains("lldb-vscode") -> DapDriverType.LLDB
                    output.contains("gdb") -> DapDriverType.GDB
                    else -> null
                }
            }
        }
    }

    /**
     * Get default search paths for DAP drivers
     */
    fun getDefaultSearchPaths(): List<String> = buildList {
        when {
            SystemInfo.isMac -> addAll(listOf(
                "/Applications/Xcode.app/Contents/Developer/usr/bin/lldb-dap",
                "/usr/local/opt/llvm/bin/lldb-dap",
                "/opt/homebrew/opt/llvm/bin/lldb-dap",
                "/usr/bin/lldb-dap",
                "/usr/local/bin/lldb-dap",
                "/usr/local/opt/llvm/bin/gdb",
                "/opt/homebrew/opt/llvm/bin/gdb",
                "/usr/bin/gdb",
                "/usr/local/bin/gdb",
            ))

            SystemInfo.isLinux -> addAll(listOf(
                "/usr/bin/lldb-dap",
                "/usr/local/bin/lldb-dap",
                "/usr/bin/gdb",
                "/usr/local/bin/gdb",
                "/snap/bin/gdb",
                "/snap/bin/gdb-dap",
            ))

            SystemInfo.isWindows -> addAll(listOf(
                "C:\\Program Files\\LLVM\\bin\\lldb-dap.exe",
                "C:\\Program Files (x86)\\LLVM\\bin\\lldb-dap.exe",
                "C:\\msys64\\mingw64\\bin\\lldb-dap.exe",
                "C:\\msys64\\mingw64\\bin\\gdb.exe",
                "C:\\Program Files\\GDB\\bin\\gdb.exe",
            ))
        }
    }

    /**
     * Find all available DAP drivers
     */
    fun findAvailableDrivers(): List<DapDriverInfo> =
        getDefaultSearchPaths().asSequence()
            .filter { path -> File(path).let { it.exists() && it.canExecute() } }
            .mapNotNull(::detectDriver)
            .toList()

    private fun detectDriver(path: String): DapDriverInfo? {
        val type = detectDriverType(path) ?: return null
        return when (type) {
            DapDriverType.GDB -> DapDriverInfo(path, type, getGdbDapSupport(path))
            DapDriverType.LLDB -> DapDriverInfo(path, type, DapSupport.Supported)
        }
    }

    /**
     * Find best available DAP driver (prefer LLDB over GDB)
     */
    fun findBestDriver(): DapDriverInfo? {
        val drivers = findAvailableDrivers()

        // Prefer LLDB over GDB
        return drivers.find { it.type == DapDriverType.LLDB }
            ?: drivers.find { it.type == DapDriverType.GDB && it.support is DapSupport.Supported }
    }

    /**
     * Validate a custom DAP driver path
     */
    fun validateDriverPath(path: String): DapDriverInfo? {
        val file = File(path)
        if (!file.exists() || !file.canExecute()) {
            return null
        }
        return detectDriver(path)
    }

    private const val DETECTION_TIMEOUT_MS = 1_500L
}
