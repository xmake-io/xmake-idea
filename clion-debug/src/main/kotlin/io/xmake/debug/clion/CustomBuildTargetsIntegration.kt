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
 * @file        CustomBuildTargetsIntegration.kt
 *
 */
package io.xmake.debug.clion

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.xmake.debug.clion.utils.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Registers each xmake target with CLion's Custom Build Targets subsystem
 * (`com.jetbrains.cidr.cpp.execution.external.build`, bundled in the `com.intellij.clion` plugin),
 * so the native "Build Target" action works and — once executable paths are wired — gutter
 * Run/Debug markers appear on `main()`.
 *
 * This is CLion-only; it is compiled into `xmake-clion-debug.jar` and invoked reflectively from the
 * main plugin via [io.xmake.debug.DebugModuleLoader] so the plugin still loads on IDEA Community.
 * The target list is passed as a JSON string (see [io.xmake.debug.CustomBuildTargetsSupport]) to
 * keep the two classloaders decoupled.
 */
object CustomBuildTargetsIntegration {

    private const val TAG = "CustomBuildTargetsIntegration"

    /** Project-tool group all xmake build/clean tools live under (namespaced, replaced on each sync). */
    private const val TOOL_GROUP = "XMake"

    /** One xmake target and how to build/clean/run it, decoded from the reflection JSON contract. */
    data class TargetSpec(
        val name: String,
        val executablePath: String?,
        val isExecutable: Boolean,
        val buildArgs: List<String>,
        val cleanArgs: List<String>,
    )

    /** Whether CLion's Custom Build Targets subsystem is present in this IDE. */
    @JvmStatic
    fun isAvailable(): Boolean = try {
        Class.forName("com.jetbrains.cidr.cpp.execution.external.build.CLionExternalBuildManager")
        true
    } catch (t: Throwable) {
        false
    }

    /**
     * Replace the plugin-managed CLion build targets with the ones described by [specJson].
     * Returns true if the sync was scheduled/applied. See the class doc for the JSON shape.
     */
    @JvmStatic
    fun syncBuildTargets(project: Project, specJson: String): Boolean {
        val spec = try {
            parseSpec(specJson)
        } catch (t: Throwable) {
            Logger.e(TAG, "Failed to parse build-target spec: ${t.message}")
            return false
        }
        if (spec == null) {
            Logger.w(TAG, "Empty or invalid build-target spec")
            return false
        }
        if (spec.targets.isEmpty()) {
            Logger.d(TAG, "No xmake targets to register")
            return false
        }
        Logger.i(TAG, "Registering ${spec.targets.size} xmake target(s) with CLion: " +
            spec.targets.joinToString { it.name })

        // setTools / setTargets mutate project-level persistent state and fire listeners, so run on
        // the EDT (this is called from xmake process-termination callbacks on background threads).
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            try {
                // The actual CLion registration lives in a Java helper: CLionProjectToolManager (and
                // some CLionExternalBuildManager members) are Kotlin `internal`, which the Kotlin
                // compiler forbids cross-module, but Java ignores.
                CLionBuildTargetRegistrar.register(
                    project, spec.xmakeBinary, spec.workingDir, spec.projectName, TOOL_GROUP, spec.targets
                )
                Logger.i(TAG, "Installed ${spec.targets.size} CLion custom build target(s)")
            } catch (t: Throwable) {
                Logger.e(TAG, "Failed to register custom build targets: ${t.message}")
                t.printStackTrace()
            }
        }
        return true
    }

    /** Parsed reflection contract: the project-wide fields plus the per-target specs. */
    private data class Spec(
        val xmakeBinary: String,
        val workingDir: String,
        val projectName: String,
        val targets: List<TargetSpec>,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun parseSpec(specJson: String): Spec? {
        if (specJson.isBlank()) return null
        val root = json.decodeFromString<JsonObject>(specJson)
        val targetsArray = root["targets"] as? JsonArray ?: JsonArray(emptyList())
        val targets = targetsArray.map { element ->
            val obj = element.jsonObject
            TargetSpec(
                name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                executablePath = obj["executablePath"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
                isExecutable = obj["isExecutable"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                buildArgs = (obj["buildArgs"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(),
                cleanArgs = (obj["cleanArgs"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(),
            )
        }.filter { it.name.isNotBlank() }
        return Spec(
            xmakeBinary = root["xmakeBinary"]?.jsonPrimitive?.content.orEmpty(),
            workingDir = root["workingDir"]?.jsonPrimitive?.content.orEmpty(),
            projectName = root["projectName"]?.jsonPrimitive?.content.orEmpty(),
            targets = targets,
        )
    }
}
