package io.xmake.debug

import com.intellij.openapi.project.Project
import com.intellij.execution.configurations.GeneralCommandLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Default debug configurations for different DAP drivers
 */
object DefaultDebugConfigurations {
    
    // Default DAP configuration
    val defaultDapConfig = mapOf(
        "stopOnEntry" to false,
        "sourceMap" to mapOf(
            "enabled" to "true"
        ),
        "showDisassembly" to "auto",
        "enablePrettyPrinting" to true,
        "timeout" to 30000,
        "memoryReference" to "hex",
        "displayFormat" to "hex",
        "maxChildren" to 1000,
        "maxArrayLength" to 1000,
        "maxStringLength" to 10000
    )
    
    // LLDB-specific default configuration
    val defaultLldbConfig = mapOf(
        "initCommands" to listOf(
            "settings set target.inline-breakpoint-strategy always",
            "settings set target.process.memory-protection-rules false",
            "settings set target.process.memory-cache-line-size 64",
            "settings set target.process.disable-aslr false",
            "settings set target.process.detach-on-error false",
            "settings set target.process.stop-on-sharedlib-events false",
            "settings set target.process.stop-on-sharedlib-load-state false",
            "settings set target.process.stop-on-plugin-load false",
            "settings set target.process.stop-on-dylib-load false",
            "settings set target.process.stop-on-dylib-unload false",
            "settings set target.process.stop-on-objc-throw false",
            "settings set target.process.stop-on-cxx-exception-break false",
            "settings set target.process.stop-on-cxx-exception-throw false",
            "settings set target.process.stop-on-catch-throw false",
            "settings set target.process.stop-on-objc-exception-bp false",
            "settings set target.process.stop-on-cxx-exception-bp false",
            "settings set target.process.stop-on-catch-bp false",
            "settings set target.process.stop-on-exception-bp false",
            "settings set target.process.stop-on-exception-throw false",
            "settings set target.process.stop-on-exception-catch false",
            "settings set target.process.stop-on-exception-rethrow false",
            "settings set target.process.stop-on-exception-terminate false",
            "settings set target.process.stop-on-exception-continue false",
            "settings set target.process.stop-on-exception-exit false",
            "settings set target.process.stop-on-exception-return false",
            "settings set target.process.stop-on-exception-abort false",
            "settings set target.process.stop-on-exception-sigsegv false",
            "settings set target.process.stop-on-exception-sigbus false",
            "settings set target.process.stop-on-exception-sigfpe false",
            "settings set target.process.stop-on-exception-sigill false",
            "settings set target.process.stop-on-exception-sigtrap false",
            "settings set target.process.stop-on-exception-sigabrt false",
            "settings set target.process.stop-on-exception-sigpipe false",
            "settings set target.process.stop-on-exception-sigalrm false",
            "settings set target.process.stop-on-exception-sigterm false",
            "settings set target.process.stop-on-exception-sigkill false",
            "settings set target.process.stop-on-exception-sigint false",
            "settings set target.process.stop-on-exception-sigquit false",
            "settings set target.process.stop-on-exception-sigstop false",
            "settings set target.process.stop-on-exception-sigtstp false",
            "settings set target.process.stop-on-exception-sigcont false",
            "settings set target.process.stop-on-exception-sigchld false",
            "settings set target.process.stop-on-exception-sigttin false",
            "settings set target.process.stop-on-exception-sigttou false",
            "settings set target.process.stop-on-exception-sigurg false",
            "settings set target.process.stop-on-exception-sigxcpu false",
            "settings set target.process.stop-on-exception-sigxfsz false",
            "settings set target.process.stop-on-exception-sigvtalrm false",
            "settings set target.process.stop-on-exception-sigprof false",
            "settings set target.process.stop-on-exception-sigusr1 false",
            "settings set target.process.stop-on-exception-sigusr2 false",
            // Memory viewing specific settings
            "settings set target.memory-module-load-level full",
            "settings set target.memory-module-load-stack-unwind yes",
            "settings set target.memory-module-load-stack-dump yes",
            "settings set target.memory-module-load-dynamic-linker yes",
            "settings set target.memory-module-load-system-libraries yes",
            "settings set target.memory-module-load-user-specified yes",
            "settings set target.memory-module-load-all yes",
            "settings set target.memory-module-load-dependents yes",
            "settings set target.memory-module-load-dependencies yes",
            "settings set target.memory-module-load-reexports yes",
            "settings set target.memory-module-load-local-symbols yes",
            "settings set target.memory-module-load-global-symbols yes",
            "settings set target.memory-module-load-external-symbols yes",
            "settings set target.memory-module-load-debug-symbols yes",
            "settings set target.memory-module-load-type-info yes",
            "settings set target.memory-module-load-objc-info yes",
            "settings set target.memory-module-load-swift-info yes",
            "settings set target.memory-module-load-rust-info yes",
            "settings set target.memory-module-load-go-info yes",
            "settings set target.memory-module-load-dlang-info yes",
            "settings set target.memory-module-load-nim-info yes",
            "settings set target.memory-module-load-zig-info yes",
            "settings set target.memory-module-load-ada-info yes",
            "settings set target.memory-module-load-fortran-info yes",
            "settings set target.memory-module-load-haskell-info yes",
            "settings set target.memory-module-load-ocaml-info yes",
            "settings set target.memory-module-load-scala-info yes",
            "settings set target.memory-module-load-kotlin-info yes",
            "settings set target.memory-module-load-java-info yes",
            "settings set target.memory-module-load-csharp-info yes",
            "settings set target.memory-module-load-vb-info yes",
            "settings set target.memory-module-load-fsharp-info yes",
            "settings set target.memory-module-load-powershell-info yes",
            "settings set target.memory-module-load-python-info yes",
            "settings set target.memory-module-load-ruby-info yes",
            "settings set target.memory-module-load-perl-info yes",
            "settings set target.memory-module-load-php-info yes",
            "settings set target.memory-module-load-javascript-info yes",
            "settings set target.memory-module-load-typescript-info yes",
            "settings set target.memory-module-load-coffeescript-info yes",
            "settings set target.memory-module-load-bash-info yes",
            "settings set target.memory-module-load-zsh-info yes",
            "settings set target.memory-module-load-fish-info yes",
            "settings set target.memory-module-load-csh-info yes",
            "settings set target.memory-module-load-tcsh-info yes",
            "settings set target.memory-module-load-ksh-info yes",
            "settings set target.memory-module-load-sh-info yes"
        )
    )
    
    // GDB-specific default configuration
    val defaultGdbConfig = mapOf(
        "initCommands" to listOf(
            "set pagination off",
            "set print pretty on",
            "set print array on",
            "set print elements 0",
            "set print null-stop on",
            "set confirm off",
            "set unwindonsignal on",
            "set step-mode on",
            "set print object on",
            "set print static-members on",
            "set print vtbl on",
            "set print demangle on",
            "set demangle-style gnu-v3",
            "set print sevenbit-strings off",
            "set print union on",
            "set print repeats 0",
            "set print frame-arguments all",
            "set print frame-info auto",
            "set print entry-values no",
            "set print raw-frame-arguments no"
        )
    )
    
    /**
     * Get default configuration for specific driver type
     */
    fun getDefaultConfig(driverType: DapDriverDetector.DapDriverType): Map<String, Any> {
        return when (driverType) {
            DapDriverDetector.DapDriverType.LLDB_DAP -> 
                defaultDapConfig + defaultLldbConfig
            DapDriverDetector.DapDriverType.GDB_DAP -> 
                defaultDapConfig + defaultGdbConfig
            else -> defaultDapConfig
        }
    }
    
    /**
     * Parse user configuration and merge with defaults
     */
    fun mergeConfigurations(
        userConfigJson: String,
        defaultConfig: Map<String, Any>
    ): Map<String, Any> {
        if (userConfigJson.isBlank()) {
            return defaultConfig
        }
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val userConfig = json.decodeFromString<JsonObject>(userConfigJson)
            
            val mergedConfig = defaultConfig.toMutableMap()
            
            userConfig.forEach { (key, value) ->
                when (value) {
                    is JsonObject -> {
                        // Handle nested objects like sourceMap
                        val existingNested = (mergedConfig[key] as? Map<String, Any>) ?: emptyMap()
                        val userNested = value.mapValues { 
                            val element = it.value
                            when {
                                element.toString().toBooleanStrictOrNull() != null -> element.toString().toBoolean()
                                element.toString().toIntOrNull() != null -> element.toString().toInt()
                                else -> element.toString()
                            }
                        }
                        mergedConfig[key] = existingNested + userNested
                    }
                    else -> {
                        // Handle primitive values and other JsonElement types
                        mergedConfig[key] = when {
                            value.toString().toBooleanStrictOrNull() != null -> value.toString().toBoolean()
                            value.toString().toIntOrNull() != null -> value.toString().toInt()
                            else -> value.toString()
                        }
                    }
                }
            }
            
            mergedConfig
        } catch (e: Exception) {
            // If JSON parsing fails, return default config
            defaultConfig
        }
    }
}
