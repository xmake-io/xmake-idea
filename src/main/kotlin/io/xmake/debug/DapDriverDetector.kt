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

/**
 * DAP driver path detection utility
 */
object DapDriverDetector {
    
    private const val TAG = "DapDriverDetector"
    
    data class DapDriverInfo(
        val path: String,
        val type: DapDriverType,
        val displayName: String
    )
    
    enum class DapDriverType(val displayName: String) {
        LLDB_DAP("LLDB DAP"),
        GDB_DAP("GDB DAP"),
        UNKNOWN("Unknown DAP")
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
            fileName.contains("gdb-dap") -> DapDriverType.GDB_DAP
            fileName.contains("lldb") -> DapDriverType.LLDB_DAP
            fileName.contains("gdb") -> DapDriverType.GDB_DAP
            else -> {
                // Try to detect by checking file content or help output
                try {
                    val process = ProcessBuilder(path, "--version").start()
                    val output = process.inputStream.bufferedReader().readText().lowercase()
                    when {
                        output.contains("lldb") -> DapDriverType.LLDB_DAP
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
                "/usr/local/opt/llvm/bin/gdb-dap",
                "/opt/homebrew/opt/llvm/bin/gdb-dap",
                "/usr/bin/gdb-dap",
                "/usr/local/bin/gdb-dap"
            ))
        } else if (SystemInfo.isLinux) {
            // Linux paths
            paths.addAll(listOf(
                "/usr/bin/lldb-dap",
                "/usr/local/bin/lldb-dap",
                "/usr/bin/gdb-dap",
                "/usr/local/bin/gdb-dap",
                "/snap/bin/lldb-dap",
                "/snap/bin/gdb-dap"
            ))
        } else if (SystemInfo.isWindows) {
            // Windows paths
            paths.addAll(listOf(
                "C:\\Program Files\\LLVM\\bin\\lldb-dap.exe",
                "C:\\Program Files (x86)\\LLVM\\bin\\lldb-dap.exe",
                "C:\\msys64\\mingw64\\bin\\lldb-dap.exe",
                "C:\\msys64\\mingw64\\bin\\gdb-dap.exe",
                "C:\\Program Files\\GDB\\bin\\gdb-dap.exe"
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
                    drivers.add(DapDriverInfo(path, type, type.displayName))
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
            ?: drivers.find { it.type == DapDriverType.GDB_DAP }
            ?: drivers.firstOrNull()
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
            DapDriverInfo(path, type, type.displayName)
        } else {
            null
        }
    }
}
