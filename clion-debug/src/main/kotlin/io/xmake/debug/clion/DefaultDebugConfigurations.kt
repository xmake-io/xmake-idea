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
 * @file        DefaultDebugConfigurations.kt
 *
 */
package io.xmake.debug.clion

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonArray
import io.xmake.debug.clion.utils.Logger

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
     * Parse user configuration and merge with defaults using safe JSON parsing
     */
    fun parseLaunchConfig(userConfigJson: String): Map<String, Any> {
        if (userConfigJson.isBlank()) {
            return emptyMap()
        }
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val userConfig = json.decodeFromString<JsonObject>(userConfigJson)
            
            val result = mutableMapOf<String, Any>()
            
            userConfig.forEach { (key, value) ->
                when (value) {
                    is JsonObject -> {
                        // Handle nested objects like sourceMap
                        val userNested = value.mapValues { 
                            convertJsonElement(it.value, key == "sourceMap" && it.key == "enabled")
                        }
                        result[key] = userNested
                    }
                    else -> {
                        // Handle primitive values using safe conversion
                        result[key] = convertJsonElement(value)
                    }
                }
            }
            
            result
        } catch (e: Exception) {
            Logger.w("DefaultDebugConfigurations", "Failed to parse user launch configuration JSON: ${e.message}", e)
            Logger.d("DefaultDebugConfigurations", "Invalid JSON content: $userConfigJson")
            // If JSON parsing fails, return empty map
            emptyMap()
        }
    }
    
    /**
     * Safely convert JsonElement to appropriate type
     */
    private fun convertJsonElement(element: JsonElement, forceString: Boolean = false): Any {
        return when {
            forceString -> {
                // Special case: sourceMap.enabled should remain as string
                when (element) {
                    is kotlinx.serialization.json.JsonPrimitive -> element.content
                    else -> element.toString()
                }
            }
            element is kotlinx.serialization.json.JsonPrimitive -> {
                when {
                    element.content == "true" || element.content == "false" -> element.content.toBoolean()
                    element.content.toLongOrNull() != null -> {
                        // Try to preserve integer type when possible
                        val longValue = element.content.toLong()
                        if (longValue.toInt().toLong() == longValue) {
                            longValue.toInt()
                        } else {
                            longValue
                        }
                    }
                    else -> element.content
                }
            }
            element is JsonArray -> {
                // Convert JSON array to List<Any>
                element.map { convertJsonElement(it) }
            }
            element is JsonObject -> {
                // Convert nested object to Map<String, Any>
                element.mapValues { convertJsonElement(it.value) }
            }
            else -> element.toString()
        }
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
