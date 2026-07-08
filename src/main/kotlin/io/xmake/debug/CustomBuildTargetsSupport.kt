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
 * @file        CustomBuildTargetsSupport.kt
 *
 */
package io.xmake.debug

import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.utils.SystemUtils
import io.xmake.utils.info.xmakeInfo
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Bridges the xmake target list to CLion's Custom Build Targets subsystem so the native
 * "Build Target" action works and gutter Run/Debug markers appear. On non-CLion IDEs the underlying
 * [DebugModuleLoader.syncBuildTargets] call is a no-op, so this is safe to invoke unconditionally.
 *
 * Only LOCAL toolkits are supported for now: CLion needs a local filesystem executable, and WSL/SSH
 * builds produce remote paths it cannot run natively.
 */
object CustomBuildTargetsSupport {

    /** Serialize the current xmake targets into the reflection JSON contract, or null if unsupported. */
    fun buildSpecJson(project: Project): String? {
        if (!SystemUtils.isXMakeProject(project)) return null
        // Toolkit resolution must NOT depend on which run configuration is selected: once the user
        // selects the native Xmake Executable config, `activatedToolkit` is null. Fall back to a
        // registered toolkit (same pattern as XMakeInfoActivity).
        val toolkit = project.activatedToolkit
            ?: ToolkitManager.getInstance().getRegisteredToolkits().firstOrNull()
            ?: return null
        if (toolkit.isOnRemote) return null

        val targets = project.xmakeInfo.targets
        if (targets.isEmpty()) return null

        val xmakeBinary = toolkit.path
        val workingDir = project.basePath ?: return null

        val spec = buildJsonObject {
            put("xmakeBinary", xmakeBinary)
            put("workingDir", workingDir)
            put("projectName", project.name)
            putJsonArray("targets") {
                targets.forEach { target ->
                    addJsonObject {
                        put("name", target)
                        // executablePath / isExecutable are populated in Slice C.
                        putJsonArray("buildArgs") {
                            add("build"); add("-y"); add(target)
                        }
                        putJsonArray("cleanArgs") {
                            add("clean"); add(target)
                        }
                    }
                }
            }
        }
        return spec.toString()
    }

    /** Register the current xmake targets with CLion, if supported. */
    fun syncTargets(project: Project) {
        val specJson = buildSpecJson(project) ?: return
        DebugModuleLoader.syncBuildTargets(project, specJson)
    }
}
