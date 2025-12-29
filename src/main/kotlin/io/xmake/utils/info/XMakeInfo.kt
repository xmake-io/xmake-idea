package io.xmake.utils.info

import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class XMakeInfo {

    var apis: XMakeApis? = null
    var architectures: XMakeArchitectures = emptyMap()
    var buildModes: XMakeBuildModes = emptyList()
    var envs: XMakeEnvs = emptyMap()
    var packages: XMakePackages = emptyList()
    var platforms: XMakePlatforms = emptyList()
    var policies: XMakePolicies = emptyMap()
    var rules: XMakeRules = emptyList()
    var targets: XMakeTargets = emptyList()
    var toolchains: XMakeToolchains = emptyMap()

    private val json = Json { ignoreUnknownKeys = true }

    fun parseApis(apiString: String): XMakeApis? {
        return try {
            json.decodeFromString<XMakeApis>(apiString).also {
                Log.info("Parsed XMake Apis: $it")
                println("Parsed XMake Apis: $it")
            }
        } catch (e: Exception) {
            Log.error("Failed to parse apis: $e")
            println("Failed to parse apis: $e")
            null
        }
    }

    fun parseArchitectures(archString: String): XMakeArchitectures {
        return try {
            json.decodeFromString<XMakeArchitectures>(archString).also {
                Log.info("Parsed XMake Architectures: $it")
                println("Parsed XMake Architectures: $it")
            }
        } catch (e: Exception) {
            Log.error("Failed to parse architectures: $e")
            println("Failed to parse architectures: $e")
            emptyMap()
        }
    }

    fun parseBuildModes(buildModeString: String): XMakeBuildModes {
        return try {
            json.decodeFromString<XMakeBuildModes>(buildModeString).also {
                Log.info("Parsed XMake BuildModes: $it")
                println("Parsed XMake BuildModes: $it")
            }
        } catch (e: Exception) {
            Log.error("Failed to parse buildmodes: $e")
            println("Failed to parse buildmodes: $e")
            emptyList()
        }
    }

    fun parseEnvs(envString: String): XMakeEnvs {
        // Todo
        return emptyMap()
    }

    fun parsePackages(packageString: String): XMakePackages {
        // Todo
        return emptyList()
    }

    fun parsePlatforms(platformString: String): XMakePlatforms {
        return try {
            json.decodeFromString<XMakePlatforms>(platformString).also {
                Log.info("Parsed XMake Platforms: $it")
                println("Parsed XMake Platforms: $it")
            }
        } catch (e: Exception) {
            Log.error("Failed to parse platforms: $e")
            println("Failed to parse platforms: $e")
            emptyList()
        }
    }

    fun parsePolicies(policyString: String): XMakePolicies {
        // Todo
        return emptyMap()
    }

    fun parseRules(ruleString: String): XMakeRules {
        return try {
            // Rules might be a list of strings or objects, need careful handling if complex
            json.decodeFromString<XMakeRules>(ruleString).also {
                Log.info("Parsed XMake Rules: $it")
                println("Parsed XMake Rules: $it")
            }
        } catch (e: Exception) {
            Log.error("Failed to parse rules: $e")
            println("Failed to parse rules: $e")
            emptyList()
        }
    }

    fun parseTargets(targetString: String): XMakeTargets {
        return try {
            // Targets might be complex objects or simple strings depending on xmake version/output
            // Assuming simple list of target names for now based on previous typealias
             json.decodeFromString<XMakeTargets>(targetString).also {
                Log.info("Parsed XMake Targets: $it")
                println("Parsed XMake Targets: $it")
            }
        } catch (e: Exception) {
            Log.error("Failed to parse targets: $e")
            println("Failed to parse targets: $e")
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
                Log.info("Parsed XMake Toolchains (JSON): $toolchains")
                println("Parsed XMake Toolchains (JSON): $toolchains")
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
             }.associate { it }.also {
                 Log.info("Parsed XMake Toolchains (Text): ${it.keys}")
                 println("Parsed XMake Toolchains (Text): ${it.keys}")
             }
        } catch (e2: Exception) {
             Log.error("Failed to parse toolchains: $e2")
             println("Failed to parse toolchains: $e2")
             emptyMap()
        }
    }

    companion object {
        val Log = logger<XMakeInfo>()

    }
}

