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
import java.io.File

/**
 * DAP driver path detection utility
 */
object DapDriverDetector {

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
                val output = GdbSupportDetector.versionOutput(path).lowercase()
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
            DapDriverType.GDB -> DapDriverInfo(path, type, GdbSupportDetector.detect(path))
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
}
