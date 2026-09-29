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
 */
package io.xmake.debug.clion.dap

import io.xmake.debug.XMakeDebugLaunch
import io.xmake.debug.clion.utils.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal object XMakeDapLaunchArguments {

    private val json = Json { ignoreUnknownKeys = true }

    fun create(launch: XMakeDebugLaunch): Map<String, Any?> = buildMap {
        putAll(defaults())
        putAll(parseUserConfiguration(launch.launchConfiguration))
        // program/cwd/env/args are owned by the XMake run configuration and cannot be overridden here.
        put("program", launch.executablePath)
        put("cwd", launch.workingDirectory)
        put("env", launch.environment)
        put("args", launch.arguments)
    }

    private fun defaults(): Map<String, Any?> = mapOf(
        "stopOnEntry" to false,
    )

    private fun parseUserConfiguration(configuration: String): Map<String, Any?> {
        if (configuration.isBlank()) return emptyMap()
        return try {
            json.decodeFromString<JsonObject>(configuration).mapValues { (_, value) -> value.toValue() }
        } catch (error: Exception) {
            Logger.w(TAG, "Failed to parse DAP launch configuration: ${error.message}", error)
            emptyMap()
        }
    }

    private fun JsonElement.toValue(): Any? = when (this) {
        JsonNull -> null
        is JsonObject -> mapValues { (_, value) -> value.toValue() }
        is JsonArray -> map { it.toValue() }
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> content
        }
    }

    private const val TAG = "XMakeDapLaunchArguments"
}
