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
 * @file        CustomBuildTargetsActivity.kt
 *
 */
package io.xmake.debug

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.xmake.utils.SystemUtils
import io.xmake.utils.info.XMakeInfo
import io.xmake.utils.info.XMakeInfoManager

/**
 * Keeps CLion's Custom Build Targets in sync with the xmake target list: registers them on project
 * open and re-registers whenever the target list is (re)probed (`XMAKE_INFO_TOPIC`, fired on toolkit
 * change / configure). No-ops on IDEA Community, where [CustomBuildTargetsSupport.syncTargets] does
 * nothing.
 */
private const val PLUGIN_ID = "io.xmake"

class CustomBuildTargetsActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!SystemUtils.isXMakeProject(project)) return

        // Register the native "Xmake Executable" run configuration type (CLion only; no-ops elsewhere).
        DebugModuleLoader.registerXMakeExecutableType(PLUGIN_ID)

        // Re-sync whenever the xmake info (targets) is refreshed.
        project.messageBus.connect().subscribe(
            XMakeInfoManager.XMAKE_INFO_TOPIC,
            object : XMakeInfoManager.XMakeInfoListener {
                override fun onXMakeInfoUpdated(xmakeInfo: XMakeInfo) {
                    CustomBuildTargetsSupport.syncTargets(project)
                }
            }
        )

        // Initial sync in case targets are already populated (no-ops if the list is empty).
        CustomBuildTargetsSupport.syncTargets(project)
    }
}
