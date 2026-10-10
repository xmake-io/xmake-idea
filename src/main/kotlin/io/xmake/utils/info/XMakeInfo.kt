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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive


data class XMakeInfo(
    val architectures: XMakeArchitectures = emptyMap(),
    val buildModes: XMakeBuildModes = emptyList(),
    val platforms: XMakePlatforms = emptyList(),
    val targets: XMakeTargets = emptyList(),
    val toolchains: XMakeToolchains = emptyMap(),
    val apis: XMakeApis = emptySet(),
) {

    companion object {

        private val json = Json { ignoreUnknownKeys = true }

        private fun <T> parseShowOutput(
            output: String,
            decode: (String) -> T,
            fromLines: (List<String>) -> T,
        ): T {
            val payload = output.lineSequence().map(String::trim)
                .lastOrNull { it.startsWith("[") || it.startsWith("{") }
            if (payload != null) {
                try {
                    return decode(payload)
                } catch (_: Exception) {
                }
            }
            return fromLines(showEntryLines(output))
        }

        private fun showEntryLines(output: String): List<String> =
            output.lineSequence().map(String::trim)
                .filter(String::isNotEmpty)
                .filterNot { it.startsWith("[") || it.startsWith("{") }
                .filterNot { DIAGNOSTIC_PREFIX.containsMatchIn(it) }
                .toList()

        private val DIAGNOSTIC_PREFIX =
            Regex("""(?i)^\s*(note|warning|error|checking|stack traceback)\s*[: ]""")

        fun parseArchitectures(output: String): XMakeArchitectures =
            parseShowOutput(
                output,
                { json.decodeFromString(it) },
                { lines ->
                    buildMap {
                        lines.forEach { line ->
                            line.split(":").takeIf { it.size == 2 }?.let { (platform, archs) ->
                                put(platform.trim(), archs.split(",").map(String::trim).filter(String::isNotEmpty))
                            }
                        }
                    }
                },
            )

        fun parseBuildModes(output: String): XMakeBuildModes =
            parseShowOutput(output, { json.decodeFromString(it) }, { it })

        fun parsePlatforms(output: String): XMakePlatforms =
            parseShowOutput(output, { json.decodeFromString(it) }, { it })

        fun parseTargets(output: String): XMakeTargets =
            parseShowOutput(output, { json.decodeFromString(it) }, { it })

        fun parseToolchains(output: String): XMakeToolchains =
            parseShowOutput(
                output,
                { payload ->
                    val element = json.parseToJsonElement(payload)
                    buildMap {
                        (element as? JsonArray)?.forEach { item ->
                            when (item) {
                                is JsonObject -> {
                                    val name = item["name"]?.jsonPrimitive?.content ?: return@forEach
                                    put(name, item["description"]?.jsonPrimitive?.content ?: "")
                                }
                                is kotlinx.serialization.json.JsonPrimitive -> put(item.content, "")
                                else -> {}
                            }
                        }
                    }
                },
                { lines ->
                    buildMap {
                        lines.forEach { line ->
                            val parts = line.split(Regex("\\s+"), limit = 2)
                            val name = parts.firstOrNull()?.replace(ANSI_ESCAPE, "") ?: return@forEach
                            put(name, parts.getOrNull(1) ?: "")
                        }
                    }
                },
            )

        fun parseApis(output: String): XMakeApis =
            parseShowOutput(
                output,
                { payload ->
                    val element = json.parseToJsonElement(payload)
                    buildSet {
                        (element as? JsonObject)?.let { obj ->
                            for (key in listOf("description_builtin_apis", "script_builtin_apis", "description_scope_apis")) {
                                obj[key]?.jsonArray?.forEach { item ->
                                    item.jsonPrimitive.content.split(".").filter(String::isNotEmpty).forEach(::add)
                                }
                            }
                        }
                    }
                },
                { emptySet() },
            )

        private val ANSI_ESCAPE = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    }
}
