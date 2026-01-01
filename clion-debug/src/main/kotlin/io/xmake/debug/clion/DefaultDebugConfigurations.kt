package io.xmake.debug.clion

/**
 * Default debug configurations for XMake debugging
 */
object DefaultDebugConfigurations {
    
    /**
     * Default DAP configuration
     */
    val defaultDapConfig: Map<String, Any> = mapOf(
        "type" to "lldb-dap",
        "name" to "XMake Debug",
        "request" to "launch",
        "stopOnEntry" to false,
        "sourceMap" to emptyMap<String, Any>(),
        "showDisassembly" to false,
        "cwd" to "\${workspaceFolder}",
        "args" to emptyList<String>(),
        "environment" to emptyMap<String, String>()
    )
    
    /**
     * Parse user launch configuration
     * Supports JSON-like key=value pairs separated by commas
     */
    fun parseLaunchConfig(config: String): Map<String, Any> {
        if (config.isBlank()) {
            return emptyMap()
        }
        
        val result = mutableMapOf<String, Any>()
        
        try {
            // Parse key=value pairs separated by commas
            config.split(",").forEach { pair ->
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    
                    // Try to parse as number or boolean, otherwise keep as string
                    result[key] = when {
                        value.toIntOrNull() != null -> value.toInt()
                        value.toBooleanStrictOrNull() != null -> value.toBoolean()
                        else -> value
                    }
                }
            }
        } catch (e: Exception) {
            Logger.w("DefaultDebugConfigurations", "Failed to parse launch config: $config", e)
        }
        
        return result
    }
    
    /**
     * Get default configuration for specific driver type
     */
    fun getDefaultConfigForDriver(driverType: String): Map<String, Any> {
        return when (driverType.lowercase()) {
            "lldb-dap" -> mapOf(
                "type" to "lldb-dap",
                "name" to "XMake LLDB Debug",
                "request" to "launch",
                "stopOnEntry" to false,
                "sourceMap" to emptyMap<String, Any>(),
                "showDisassembly" to false,
                "cwd" to "\${workspaceFolder}",
                "args" to emptyList<String>(),
                "environment" to emptyMap<String, String>()
            )
            "gdb-dap" -> mapOf(
                "type" to "gdb-dap",
                "name" to "XMake GDB Debug",
                "request" to "launch",
                "stopOnEntry" to false,
                "cwd" to "\${workspaceFolder}",
                "args" to emptyList<String>(),
                "environment" to emptyMap<String, String>()
            )
            else -> defaultDapConfig
        }
    }
}
