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
 * @file        XMakeProfileAction.kt
 *
 */
package io.xmake.actions.selector

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.options.ShowSettingsUtil
import io.xmake.project.XMakeSettingsConfigurable
import io.xmake.project.xmakeSettings
import io.xmake.shared.XMakeReconfigure
import io.xmake.shared.xmakeConfigurationOrNull
import io.xmake.utils.SystemUtils
import javax.swing.JComponent

/**
 * Toolbar dropdown for the active xmake profile (a named bundle of `xmake f` inputs —
 * platform/arch/toolchain/build dir/extra args, see [io.xmake.project.XMakeProfile]), placed left
 * of the build-mode selector. Switching profiles behaves like switching the mode: it reconfigures
 * immediately (which also re-syncs the CLion custom build targets, keeping the "Xmake Executable"
 * run configs pointed at the right binaries) or defers to the next build, per the auto-reload
 * setting. Profiles are managed in Settings > Build > Xmake.
 */
class XMakeProfileAction : ComboBoxAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation
        if (project == null || !SystemUtils.isXMakeProject(project)) {
            presentation.isEnabledAndVisible = false
            return
        }
        presentation.isEnabledAndVisible = true
        presentation.text = project.xmakeSettings.activeProfile.name
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return group
        project.xmakeSettings.state.profiles.map { it.name }.distinct().forEach { name ->
            group.add(object : AnAction(name) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (project.xmakeSettings.state.activeProfileName != name) {
                        project.xmakeSettings.state.activeProfileName = name
                        if (project.xmakeSettings.state.autoReloadConfigOnSwitch) {
                            XMakeReconfigure.reconfigure(project)
                        } else {
                            project.xmakeConfigurationOrNull?.changed = true
                        }
                    }
                }
            })
        }
        group.add(Separator.getInstance())
        group.add(object : AnAction("Edit Profiles…") {
            override fun actionPerformed(e: AnActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, XMakeSettingsConfigurable::class.java)
            }
        })
        return group
    }
}
