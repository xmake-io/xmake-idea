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
 * @file        XMakeModeAction.kt
 *
 */
package io.xmake.actions.selector

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.Project
import io.xmake.project.xmakeSettings
import io.xmake.shared.XMakeReconfigure
import io.xmake.shared.xmakeConfigurationOrNull
import io.xmake.utils.SystemUtils
import io.xmake.utils.info.xmakeInfo
import javax.swing.JComponent

/**
 * Toolbar dropdown for the xmake build mode, placed in the run-widget area next to the run-config
 * (target) selector. Unlike the old [XMakeBuildModeAction], this edits the **project-level**
 * configuration ([io.xmake.project.XMakeSettings]) so it works with the native "Xmake Executable"
 * run config selected, and auto-reconfigures on change (picking `debug`/`releasedbg` gives the
 * debugger symbols to bind breakpoints).
 */
class XMakeModeAction : ComboBoxAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation
        if (project == null || !SystemUtils.isXMakeProject(project)) {
            presentation.isEnabledAndVisible = false
            return
        }
        presentation.isEnabledAndVisible = true
        presentation.text = project.xmakeSettings.state.buildMode
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return group
        modeOptions(project).distinct().forEach { mode ->
            group.add(object : AnAction(mode) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (project.xmakeSettings.state.buildMode != mode) {
                        project.xmakeSettings.state.buildMode = mode
                        if (project.xmakeSettings.state.autoReloadConfigOnSwitch) {
                            XMakeReconfigure.reconfigure(project)
                        } else {
                            project.xmakeConfigurationOrNull?.changed = true
                        }
                    }
                }
            })
        }
        return group
    }

    /** Modes the project actually defines (via `add_rules("mode.*")`), or a sensible default. */
    private fun modeOptions(project: Project): List<String> {
        val probed = project.xmakeInfo.buildModes.map { it.removePrefix("mode.") }
        return probed.ifEmpty { listOf("release", "debug") }
    }
}
