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
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import io.xmake.project.profile.ui.XMakeBuildProfilesDialog
import io.xmake.run.target.activeOrSingleXMakeBuildProfile

class EditXMakeBuildProfilesAction : XMakeProjectAction() {
    override fun update(e: AnActionEvent) {
        super.update(e)
        if (!e.presentation.isVisible) return

        e.presentation.icon = if (e.place == EXECUTION_TARGETS_COMBO_ACTION_PLACE) {
            null
        } else {
            AllIcons.General.Settings
        }
    }

    override fun execute(project: Project) {
        XMakeBuildProfilesDialog(project, project.activeOrSingleXMakeBuildProfile?.id).show()
    }
}
