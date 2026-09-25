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
package io.xmake.actions

import com.intellij.execution.actions.EXECUTION_TARGETS_COMBO_ACTION_PLACE
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.execution.RunManager
import io.xmake.project.profile.ui.XMakeBuildProfilesConfigurable
import io.xmake.project.directory.hasXMakeProjectDirectorySource
import io.xmake.project.XMakeSettingsConfigurable
import io.xmake.run.XMakeRunConfiguration

class EditXMakeBuildProfilesAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (!project.hasXMakeProjectDirectorySource) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, XMakeSettingsConfigurable::class.java)
            return
        }
        ShowSettingsUtil.getInstance().showSettingsDialog(project, XMakeBuildProfilesConfigurable::class.java)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val hasDirectorySource = project.hasXMakeProjectDirectorySource
        e.presentation.isVisible = true
        if (e.place == EXECUTION_TARGETS_COMBO_ACTION_PLACE) {
            e.presentation.isVisible =
                RunManager.getInstance(project).selectedConfiguration?.configuration is XMakeRunConfiguration
        }
        e.presentation.isEnabled = true
        e.presentation.text = if (hasDirectorySource) {
            EDIT_TEXT
        } else {
            CONFIGURE_TEXT
        }
        e.presentation.description = if (hasDirectorySource) {
            "Edit XMake profiles in Settings."
        } else {
            "Select the directory containing the project root xmake.lua."
        }
        e.presentation.icon = if (e.place == EXECUTION_TARGETS_COMBO_ACTION_PLACE) {
            null
        } else {
            AllIcons.General.Settings
        }
    }

    private companion object {
        const val EDIT_TEXT = "Edit XMake Profiles..."
        const val CONFIGURE_TEXT = "Configure XMake Project..."
    }
}
