package io.xmake.debug

import com.intellij.openapi.util.SystemInfo
import java.io.File
import io.xmake.utils.Logger

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
        
        return DapDriverInfo(path, type, type.displayName)
    }
}
