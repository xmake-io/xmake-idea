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
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * DAP driver path detection utility
 */
object DapDriverDetector {

    data class DapDriverInfo(
        val path: String,
        val type: DapDriverType,
        val displayName: String,
        val dapCapable: Boolean = true,
        val diagnostics: String? = null,
        /** True only when the unsupported verdict rests on parsed version evidence; a failed
         *  probe is reported as unverifiable instead of unsupported. */
        val unsupportedConfirmed: Boolean = false
    )
    
    enum class DapDriverType(val displayName: String) {
        LLDB_DAP("LLDB DAP"),
        GDB_DAP("GDB DAP"),
        UNKNOWN("Unknown DAP")
    }

    private enum class ProbeOutcome { EXITED, LAUNCH_FAILED, TIMED_OUT }

    private data class ProcessResult(
        val outcome: ProbeOutcome,
        val exitCode: Int? = null,
        val output: String = "",
        val errorText: String? = null
    )

    private data class GdbDapCapability(
        val dapCapable: Boolean,
        val versionText: String?,
        val diagnostics: String?,
        val unsupportedConfirmed: Boolean = false,
    )

    /** The cache key includes the binary's timestamp and size, so an upgrade invalidates it. */
    private data class DriverIdentity(val path: String, val lastModified: Long, val size: Long) {
        companion object {
            fun of(path: String): DriverIdentity {
                val file = File(path)
                return DriverIdentity(path, file.lastModified(), file.length())
            }
        }
    }

    private val gdbCapabilityCache: MutableMap<DriverIdentity, GdbDapCapability> = mutableMapOf()

    /** Synchronous probe helper; must be called off the EDT. [CapturingProcessHandler] reads
     *  both streams concurrently, so a full pipe can never fake a timeout. */
    private fun runProcess(executable: String, args: List<String>): ProcessResult {
        val process = try {
            ProcessBuilder(listOf(executable) + args).start()
        } catch (error: Throwable) {
            return ProcessResult(ProbeOutcome.LAUNCH_FAILED, errorText = error.message ?: error.javaClass.simpleName)
        }
        val output = CapturingProcessHandler(process, Charsets.UTF_8, "$executable ${args.joinToString(" ")}")
            .runProcess(PROBE_TIMEOUT_MS.toInt(), true)
        val text = (output.stdout + "\n" + output.stderr).trim()
        return if (output.isTimeout) {
            ProcessResult(ProbeOutcome.TIMED_OUT, output = text)
        } else {
            ProcessResult(ProbeOutcome.EXITED, exitCode = output.exitCode, output = text)
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
        val identity = DriverIdentity.of(path)
        gdbCapabilityCache[identity]?.let { return it }

        val versionResult = runProcess(path, listOf("--version"))
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
                    "GDB ${major}.${minor} detected; DAP requires GDB 14.1+.",
                    unsupportedConfirmed = true
                )
            }
            gdbCapabilityCache[identity] = capability
            return capability
        }

        val dapProbe = runProcess(path, listOf("-i", "dap", "--version"))
        val probeOutput = truncateDiagnostics(dapProbe.output)
        val probeDiagnostics = when (dapProbe.outcome) {
            ProbeOutcome.LAUNCH_FAILED ->
                "Failed to launch the DAP probe via $path: ${dapProbe.errorText}"
            ProbeOutcome.TIMED_OUT ->
                "Timed out probing the DAP interpreter via $path -i dap --version"
            ProbeOutcome.EXITED if dapProbe.exitCode != 0 ->
                if (probeOutput.isBlank()) {
                    "Failed to probe the DAP interpreter via $path -i dap --version, exit=${dapProbe.exitCode}"
                } else {
                    "Failed to probe the DAP interpreter, exit=${dapProbe.exitCode}: $probeOutput"
                }
            ProbeOutcome.EXITED -> null
        }
        val dapCapable = probeDiagnostics == null

        val capability = GdbDapCapability(dapCapable, versionText, probeDiagnostics)
        gdbCapabilityCache[identity] = capability
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
                val result = runProcess(path, listOf("--version"))
                val output = result.output.lowercase()
                when {
                    output.contains("lldb-dap") || output.contains("lldb-vscode") -> DapDriverType.LLDB_DAP
                    output.contains("gdb") -> DapDriverType.GDB_DAP
                    else -> DapDriverType.UNKNOWN
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

    private fun detectDriver(path: String): DapDriverInfo? = when (val type = detectDriverType(path)) {
        DapDriverType.UNKNOWN -> null
        DapDriverType.GDB_DAP -> detectGdbDriver(path, type)
        DapDriverType.LLDB_DAP -> DapDriverInfo(path, type, type.displayName)
    }

    private fun detectGdbDriver(path: String, type: DapDriverType): DapDriverInfo {
        val capability = getGdbDapCapability(path)
        val displayName = when {
            capability.dapCapable -> type.displayName
            capability.unsupportedConfirmed -> "${type.displayName} (need 14.1+)"
            else -> "${type.displayName} (unavailable)"
        }
        return DapDriverInfo(
            path = path,
            type = type,
            displayName = displayName,
            dapCapable = capability.dapCapable,
            diagnostics = capability.diagnostics ?: capability.versionText,
            unsupportedConfirmed = capability.unsupportedConfirmed,
        )
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
        return detectDriver(path)
    }

    private const val PROBE_TIMEOUT_MS = 1_500L
}
