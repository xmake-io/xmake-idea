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
 * @file        XMakeInfo.kt
 *
 */
package io.xmake.utils.info

import io.xmake.utils.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class XMakeInfo {

    var architectures: XMakeArchitectures = emptyMap()
    var buildModes: XMakeBuildModes = emptyList()
    var platforms: XMakePlatforms = emptyList()
    var targets: XMakeTargets = emptyList()
    var toolchains: XMakeToolchains = emptyMap()
    var apis: XMakeApis = emptySet()

    private val json = Json { ignoreUnknownKeys = true }

    fun parseArchitectures(archString: String): XMakeArchitectures {
        try {
            return json.decodeFromString<XMakeArchitectures>(archString)
        } catch (e: Exception) {
            // Fallthrough to text parsing
        }

        return try {
            val architectures = mutableMapOf<String, List<String>>()
            archString.split("\n").forEach { line ->
                val parts = line.split(":")
                if (parts.size == 2) {
                    val platform = parts[0].trim()
                    val archs = parts[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    architectures[platform] = archs
                }
            }
            architectures
        } catch (e: Exception) {
            Logger.e("Failed to parse architectures: $e")
            emptyMap()
        }
    }

    fun parseBuildModes(buildModeString: String): XMakeBuildModes {
        try {
            return json.decodeFromString<XMakeBuildModes>(buildModeString)
        } catch (e: Exception) {
            // Fallthrough
        }

        return try {
            val modes = buildModeString.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            modes
        } catch (e: Exception) {
            Logger.e("Failed to parse buildmodes: $e")
            emptyList()
        }
    }

    fun parsePlatforms(platformString: String): XMakePlatforms {
        try {
            return json.decodeFromString<XMakePlatforms>(platformString)
        } catch (e: Exception) {
            // Fallthrough
        }

        return try {
            val platforms = platformString.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            platforms
        } catch (e: Exception) {
            Logger.e("Failed to parse platforms: $e")
            emptyList()
        }
    }

    fun parseTargets(targetString: String): XMakeTargets {
        try {
            return json.decodeFromString<XMakeTargets>(targetString)
        } catch (e: Exception) {
            // Fallthrough
        }

        return try {
            val targets = targetString.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            targets
        } catch (e: Exception) {
            Logger.w("Failed to parse targets: $e")
            emptyList()
        }
    }

    fun parseToolchains(toolchainString: String): XMakeToolchains {
        try {
            val element = json.decodeFromString<JsonElement>(toolchainString)
            if (element is JsonArray) {
                val toolchains = mutableMapOf<String, String>()
                for (item in element) {
                    if (item is JsonObject) {
                        val name = item["name"]?.jsonPrimitive?.content ?: continue
                        val desc = item["description"]?.jsonPrimitive?.content ?: ""
                        toolchains[name] = desc
                    } else if (item is kotlinx.serialization.json.JsonPrimitive) {
                        toolchains[item.content] = ""
                    }
                }
                return toolchains
            }
        } catch (e: Exception) {
            // Not JSON or failed to parse, fall through to text parsing
        }

        return try {
            // Fallback to text parsing
             toolchainString.split("\n").mapNotNull { line ->
                 val parts = line.trim().split(Regex("\\s+"), limit = 2)
                 if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                     val name = parts[0].replace(Regex("\u001B\\[[0-9;]*[A-Za-z]"), "")
                     val desc = if (parts.size > 1) parts[1] else ""
                     name to desc
                 } else null
             }.associate { it }
        } catch (e: Exception) {
            Logger.w("Failed to parse toolchains: $e")
            emptyMap()
        }
    }

    fun parseApis(apiString: String): XMakeApis {
        try {
            val element = json.decodeFromString<JsonElement>(apiString)
            if (element is JsonObject) {
                val apis = mutableSetOf<String>()
                val keysToInclude = listOf("description_builtin_apis", "script_builtin_apis", "description_scope_apis")
                for (key in keysToInclude) {
                    element[key]?.jsonArray?.forEach {
                        val content = it.jsonPrimitive.content
                        content.split(".").forEach { part ->
                            if (part.isNotEmpty()) {
                                apis.add(part)
                            }
                        }
                    }
                }
                return apis
            }
        } catch (e: Exception) {
            Logger.w("Failed to parse apis: $e")
        }
        return emptySet()
    }
}

