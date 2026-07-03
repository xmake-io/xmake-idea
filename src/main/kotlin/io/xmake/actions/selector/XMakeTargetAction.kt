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
 * @file        XMakeTargetAction.kt
 *
 */
package io.xmake.actions.selector

import com.intellij.openapi.project.Project
import io.xmake.run.XMakeRunConfiguration
import io.xmake.utils.info.xmakeInfo

/** Toolbar dropdown for selecting the xmake target to build/run. */
class XMakeTargetAction : XMakeSelectorAction() {

    // The target is chosen at build/run time (xmake build/run <target>), not via `xmake f`.
    override val requiresReconfigure: Boolean = false

    override fun currentValue(config: XMakeRunConfiguration): String = config.runTarget

    override fun options(project: Project, config: XMakeRunConfiguration): List<String> {
        // Mirror the run-config editor (XMakeRunConfigurationEditor.kt:134-140): detected targets
        // plus "default", and "all" which runCommandLine handles specially.
        val targets = if (project.xmakeInfo.targets.isNotEmpty()) {
            project.xmakeInfo.targets.plus("default")
        } else {
            listOf("default")
        }
        return targets.plus("all")
    }

    override fun applyValue(config: XMakeRunConfiguration, value: String) {
        config.runTarget = value
    }
}
