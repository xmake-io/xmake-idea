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

import com.intellij.openapi.util.SystemInfo
import io.xmake.utils.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * DAP driver path detection utility
 */
object DapDriverDetector {
    
    private const val TAG = "DapDriverDetector"
    
    data class DapDriverInfo(
        val path: String,
        val type: DapDriverType,
        val displayName: String,
        val dapCapable: Boolean = true,
        val diagnostics: String? = null
    )
    
    enum class DapDriverType(val displayName: String) {
        LLDB_DAP("LLDB DAP"),
        GDB_DAP("GDB DAP"),
        UNKNOWN("Unknown DAP")
    }

    private data class ProcessResult(
        val exitCode: Int?,
        val output: String,
        val timedOut: Boolean
    )

    private data class GdbDapCapability(
        val dapCapable: Boolean,
        val versionText: String?,
        val diagnostics: String?
    )

    private val gdbCapabilityCache: MutableMap<String, GdbDapCapability> = mutableMapOf()

    private fun runProcess(
        executable: String,
        args: List<String>,
        timeoutMillis: Long
    ): ProcessResult {
        return try {
            val pb = ProcessBuilder(listOf(executable) + args)
            pb.redirectErrorStream(true)
            val process = pb.start()
            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroy()
                return ProcessResult(exitCode = null, output = "", timedOut = true)
            }
            val output = process.inputStream.bufferedReader().readText()
            ProcessResult(exitCode = process.exitValue(), output = output, timedOut = false)
        } catch (e: Throwable) {
            ProcessResult(exitCode = null, output = e.message ?: "", timedOut = false)
        }
    }

    private fun parseGdbVersion(output: String): Pair<Int, Int>? {
        val firstLine = output.lineSequence().firstOrNull()?.trim().orEmpty()
        if (firstLine.isBlank()) return null
        val match = Regex("""\b(\d+)\.(\d+)(?:\.\d+)?\b""").find(firstLine) ?: return null
        val major = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minor = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        return major to minor
    }

    private fun truncateDiagnostics(text: String, maxChars: Int = 600): String {
        val trimmed = text.trim()
        if (trimmed.length <= maxChars) return trimmed
        return trimmed.take(maxChars) + "…"
    }

    @Synchronized
    private fun getGdbDapCapability(path: String): GdbDapCapability {
        gdbCapabilityCache[path]?.let { return it }

        val versionResult = runProcess(path, listOf("--version"), 1500)
        val versionText = versionResult.output.lineSequence().firstOrNull()?.trim()
        val parsed = parseGdbVersion(versionResult.output)
        if (parsed != null) {
            val (major, minor) = parsed
            val dapCapable = major > 14 || (major == 14 && minor >= 1)
            val capability = if (dapCapable) {
                GdbDapCapability(true, versionText, null)
            } else {
                GdbDapCapability(
                    false,
                    versionText,
                    "GDB ${major}.${minor} detected; DAP requires GDB 14.1+."
                )
            }
            gdbCapabilityCache[path] = capability
            return capability
        }

        val dapProbe = runProcess(path, listOf("-i", "dap", "--version"), 1500)
        val probeOutput = truncateDiagnostics(dapProbe.output)
        val probeDiagnostics = if (dapProbe.timedOut) {
            "Timed out probing DAP interpreter via: $path -i dap --version"
        } else if (dapProbe.exitCode != null && dapProbe.exitCode != 0) {
            if (probeOutput.isBlank()) {
                "Failed probing DAP interpreter via: $path -i dap --version (exit=${dapProbe.exitCode})"
            } else {
                "Failed probing DAP interpreter (exit=${dapProbe.exitCode}): $probeOutput"
            }
        } else {
            null
        }
        val dapCapable = probeDiagnostics == null

        val capability = GdbDapCapability(dapCapable, versionText, probeDiagnostics)
        gdbCapabilityCache[path] = capability
        return capability
    }
    
    /**
     * Detect DAP driver type based on file name and content
     */
    fun detectDriverType(path: String): DapDriverType {
        val file = File(path)
        if (!file.exists()) {
            return DapDriverType.UNKNOWN
        }
        
        val fileName = file.name.lowercase()
        return when {
            fileName.contains("lldb-dap") -> DapDriverType.LLDB_DAP
            fileName.contains("lldb-vscode") -> DapDriverType.LLDB_DAP
            fileName.contains("gdb") && !fileName.contains("lldb") -> DapDriverType.GDB_DAP
            else -> {
                try {
                    val result = runProcess(path, listOf("--version"), 1500)
                    val output = result.output.lowercase()
                    when {
                        output.contains("lldb-dap") || output.contains("lldb-vscode") -> DapDriverType.LLDB_DAP
                        output.contains("gdb") -> DapDriverType.GDB_DAP
                        else -> DapDriverType.UNKNOWN
                    }
                } catch (e: Exception) {
                    Logger.d(TAG, "Failed to get driver version for $path: ${e.message}")
                    DapDriverType.UNKNOWN
                }
            }
        }
    }
    
    /**
     * Get default search paths for DAP drivers
     */
    fun getDefaultSearchPaths(): List<String> {
        val paths = mutableListOf<String>()
        
        if (SystemInfo.isMac) {
            // macOS paths
            paths.addAll(listOf(
                "/Applications/Xcode.app/Contents/Developer/usr/bin/lldb-dap",
                "/usr/local/opt/llvm/bin/lldb-dap",
                "/opt/homebrew/opt/llvm/bin/lldb-dap",
                "/usr/bin/lldb-dap",
                "/usr/local/bin/lldb-dap",
                "/usr/local/opt/llvm/bin/gdb",
                "/opt/homebrew/opt/llvm/bin/gdb",
                "/usr/bin/gdb",
                "/usr/local/bin/gdb"
            ))
        } else if (SystemInfo.isLinux) {
            // Linux paths
            paths.addAll(listOf(
                "/usr/bin/lldb-dap",
                "/usr/local/bin/lldb-dap",
                "/usr/bin/gdb",
                "/usr/local/bin/gdb",
                "/snap/bin/gdb",
                "/snap/bin/gdb-dap"
            ))
        } else if (SystemInfo.isWindows) {
            // Windows paths
            paths.addAll(listOf(
                "C:\\Program Files\\LLVM\\bin\\lldb-dap.exe",
                "C:\\Program Files (x86)\\LLVM\\bin\\lldb-dap.exe",
                "C:\\msys64\\mingw64\\bin\\lldb-dap.exe",
                "C:\\msys64\\mingw64\\bin\\gdb.exe",
                "C:\\Program Files\\GDB\\bin\\gdb.exe"
            ))
        }
        
        return paths
    }
    
    /**
     * Find all available DAP drivers
     */
    fun findAvailableDrivers(): List<DapDriverInfo> {
        val drivers = mutableListOf<DapDriverInfo>()
        
        for (path in getDefaultSearchPaths()) {
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                val type = detectDriverType(path)
                if (type != DapDriverType.UNKNOWN) {
                    if (type == DapDriverType.GDB_DAP) {
                        val capability = getGdbDapCapability(path)
                        val displayName = if (capability.dapCapable) {
                            type.displayName
                        } else {
                            "${type.displayName} (need 14.1+)"
                        }
                        drivers.add(
                            DapDriverInfo(
                                path = path,
                                type = type,
                                displayName = displayName,
                                dapCapable = capability.dapCapable,
                                diagnostics = capability.diagnostics ?: capability.versionText
                            )
                        )
                    } else {
                        drivers.add(DapDriverInfo(path, type, type.displayName, dapCapable = true))
                    }
                }
            }
        }
        
        return drivers
    }
    
    /**
     * Find best available DAP driver (prefer LLDB over GDB)
     */
    fun findBestDriver(): DapDriverInfo? {
        val drivers = findAvailableDrivers()
        
        // Prefer LLDB over GDB
        return drivers.find { it.type == DapDriverType.LLDB_DAP }
            ?: drivers.find { it.type == DapDriverType.GDB_DAP && it.dapCapable }
            ?: drivers.find { it.dapCapable }
    }
    
    /**
     * Validate a custom DAP driver path
     */
    fun validateDriverPath(path: String): DapDriverInfo? {
        val file = File(path)
        if (!file.exists() || !file.canExecute()) {
            return null
        }
        
        val type = detectDriverType(path)
        return if (type != DapDriverType.UNKNOWN) {
            if (type == DapDriverType.GDB_DAP) {
                val capability = getGdbDapCapability(path)
                val displayName = if (capability.dapCapable) {
                    type.displayName
                } else {
                    "${type.displayName} (need 14.1+)"
                }
                DapDriverInfo(
                    path = path,
                    type = type,
                    displayName = displayName,
                    dapCapable = capability.dapCapable,
                    diagnostics = capability.diagnostics ?: capability.versionText
                )
            } else {
                DapDriverInfo(path, type, type.displayName, dapCapable = true)
            }
        } else {
            null
        }
    }
}
