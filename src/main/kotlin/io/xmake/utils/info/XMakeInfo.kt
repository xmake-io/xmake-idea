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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive


/** The api set the info manager probes for Lua completion. */
data class XMakeInfo(
    val apis: XMakeApis = emptySet(),
) {

    companion object {

        private val json = Json { ignoreUnknownKeys = true }

        private fun <T> parseShowOutput(
            output: String,
            decode: (String) -> T,
            fromLines: (List<String>) -> T,
        ): T {
            // xmake emits color codes even when piped; every listing parser sees the stripped text.
            val text = output.replace(ANSI_ESCAPE, "")
            val payload = text.lineSequence().map(String::trim)
                .lastOrNull { it.startsWith("[") || it.startsWith("{") }
            if (payload != null) {
                try {
                    return decode(payload)
                } catch (_: Exception) {
                }
            }
            return fromLines(showEntryLines(text))
        }

        private fun showEntryLines(output: String): List<String> =
            output.lineSequence().map(String::trim)
                .filter(String::isNotEmpty)
                .filterNot { it.startsWith("[") || it.startsWith("{") }
                .filterNot { DIAGNOSTIC_PREFIX.containsMatchIn(it) }
                .toList()

        private val DIAGNOSTIC_PREFIX =
            Regex("""(?i)^\s*(note|warning|error|checking|stack traceback)\s*[: ]""")

        private val WHITESPACE = Regex("\\s+")

        private fun plainTokens(line: String): List<String> =
            line.replace(',', ' ').split(WHITESPACE).filter(String::isNotEmpty)

        fun parseArchitectures(output: String): XMakeArchitectures =
            parseShowOutput(
                output,
                { json.decodeFromString(it) },
                { lines ->
                    buildMap {
                        lines.forEach { line ->
                            // Plain listings print "platform arch arch ..."; older text used "platform: arch, arch".
                            val (platform, archs) = if (':' in line) {
                                line.substringBefore(':').trim() to line.substringAfter(':')
                            } else {
                                line.substringBefore(' ').trim() to line.substringAfter(' ', "")
                            }
                            val names = plainTokens(archs)
                            if (platform.isNotEmpty() && names.isNotEmpty()) put(platform, names)
                        }
                    }
                },
            )

        fun parseBuildModes(output: String): XMakeBuildModes =
            parseShowOutput(output, { json.decodeFromString(it) }, { lines -> lines.flatMap(::plainTokens) })

        fun parsePlatforms(output: String): XMakePlatforms =
            parseShowOutput(output, { json.decodeFromString(it) }, { lines -> lines.flatMap(::plainTokens) })

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
                            val name = parts.firstOrNull() ?: return@forEach
                            put(name, parts.getOrNull(1) ?: "")
                        }
                    }
                },
            )

        /** Parses the template kinds per language id from a template listing. Accepts both
         *  forms a listing takes: the json object of the lua exporter, and the indented
         *  `create --list` tree of xmake 3.x (groups unindented, languages one level in,
         *  `- kind` entries two levels in). */
        fun parseProjectTemplates(output: String): XMakeProjectTemplates {
            val text = output.replace(ANSI_ESCAPE, "")
            val payload = text.lineSequence().map(String::trim).lastOrNull { it.startsWith("{") }
            if (payload != null) {
                val exported = parseTemplateExport(payload)
                if (exported.isNotEmpty()) return exported
            }
            return parseTemplateTree(text)
        }

        private fun parseTemplateExport(payload: String): XMakeProjectTemplates = try {
            val element = json.parseToJsonElement(payload)
            buildMap {
                (element as? JsonObject)?.forEach { (language, kinds) ->
                    val names = (kinds as? JsonArray)
                        ?.mapNotNull { item -> (item as? JsonPrimitive)?.content?.takeIf(String::isNotEmpty) }
                        .orEmpty()
                    if (names.isNotEmpty()) put(language, names)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }

        /** Diagnostic lines (`note:`, `error:`, ...) start unindented, so they read as group
         *  headers and are skipped like the `builtin` header itself. */
        private fun parseTemplateTree(text: String): XMakeProjectTemplates = buildMap<String, MutableList<String>> {
            var language: String? = null
            text.lineSequence().forEach { line ->
                val indent = line.indexOfFirst { !it.isWhitespace() }
                if (indent < 0) return@forEach
                val content = line.substring(indent)
                when {
                    indent == 0 -> language = null
                    content.startsWith("- ") -> content.removePrefix("- ")
                        .takeIf(String::isNotEmpty)
                        ?.let { kind ->
                            language?.let { current ->
                                getOrPut(current) { mutableListOf() }.add(kind)
                            }
                        }

                    else -> language = content
                }
            }
        }

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
