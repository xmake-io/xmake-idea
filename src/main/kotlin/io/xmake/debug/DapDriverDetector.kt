package io.xmake.debug

import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * DAP driver path detection utility
 */
object DapDriverDetector {
    
    data class DapDriverInfo(
        val path: String,
        val type: DapDriverType,
        val displayName: String
    )
    
    enum class DapDriverType {
        LLDB_DAP,
        GDB_DAP,
        UNKNOWN
    }
    
    /**
     * Detect DAP driver type based on file name and content
     */
    fun detectDriverType(path: String): DapDriverType {
        val file = File(path)
        if (!file.exists()) return DapDriverType.UNKNOWN
        
        val fileName = file.name.lowercase()
        return when {
            fileName.contains("lldb") && fileName.contains("dap") -> DapDriverType.LLDB_DAP
            fileName.contains("gdb") && fileName.contains("dap") -> DapDriverType.GDB_DAP
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
                    val displayName = when (type) {
                        DapDriverType.LLDB_DAP -> "LLDB DAP"
                        DapDriverType.GDB_DAP -> "GDB DAP"
                        DapDriverType.UNKNOWN -> "Unknown DAP"
                    }
                    drivers.add(DapDriverInfo(path, type, displayName))
                }
            }
        }
        
        return drivers
    }
    
    /**
     * Find the best available DAP driver (prefer LLDB over GDB)
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
        if (type == DapDriverType.UNKNOWN) {
            return null
        }
        
        val displayName = when (type) {
            DapDriverType.LLDB_DAP -> "LLDB DAP"
            DapDriverType.GDB_DAP -> "GDB DAP"
            DapDriverType.UNKNOWN -> "Unknown DAP"
        }
        
        return DapDriverInfo(path, type, displayName)
    }
}
