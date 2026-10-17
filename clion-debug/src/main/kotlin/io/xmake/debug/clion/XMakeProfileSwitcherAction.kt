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
package io.xmake.debug.clion

import com.intellij.cidr.debugger.profiles.CidrDebugProfile
import com.intellij.cidr.debugger.profiles.CidrDebugProfileManager
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import io.xmake.run.XMakeProfileRunConfiguration
import io.xmake.run.target.XMakeBuildProfileExecutionTarget
import javax.swing.JComponent

/**
 * A toolbar combobox with "XMake Profiles" and "Debug Profiles" sections, mirroring CLion's
 * native multi-switcher; the debug-profile helpers degrade to an empty section on API breakage.
 */
class XMakeProfileSwitcherAction : ComboBoxAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.isDisposed || selectedConfiguration(project) !is XMakeProfileRunConfiguration) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isEnabledAndVisible = true
        e.presentation.text = switcherText(project)
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return DefaultActionGroup()
        val group = DefaultActionGroup()
        val targets = profileTargets(project)
        if (targets.isNotEmpty()) {
            group.add(Separator.create(XMAKE_SECTION))
            targets.forEach { target -> group.add(SelectXMakeProfileAction(project, target)) }
            group.add(EditProfilesAction(XMAKE_SECTION, XMAKE_CONFIGURABLE))
        }
        val profiles = debugProfiles(project)
        if (profiles.isNotEmpty()) {
            group.add(Separator.create(DEBUG_SECTION))
            profiles.forEach { profile -> group.add(SelectDebugProfileAction(project, profile)) }
            group.add(EditProfilesAction(DEBUG_SECTION, DEBUG_CONFIGURABLE))
        }
        Log.info("XMake profile switcher popup: ${targets.size} targets, ${profiles.size} debug profiles")
        return group
    }

    // The empty slot points at the entry that sets a debugger; none is picked silently.
    private fun switcherText(project: Project): String =
        listOfNotNull(
            (ExecutionTargetManager.getActiveTarget(project) as? XMakeBuildProfileExecutionTarget)?.displayName,
            currentDebugProfile(project)?.name ?: EDIT_DEBUG_PROFILES,
        ).joinToString(" | ")

    private fun profileTargets(project: Project): List<XMakeBuildProfileExecutionTarget> {
        val configuration = selectedConfiguration(project) ?: return emptyList()
        if (configuration !is XMakeProfileRunConfiguration) return emptyList()
        return targetsFor(project, configuration)
    }

    private class SelectXMakeProfileAction(
        private val project: Project,
        private val target: XMakeBuildProfileExecutionTarget,
    ) : AnAction(target.displayName) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            // Id, not instance: the manager rebuilds target instances.
            val activeProfileId =
                (ExecutionTargetManager.getActiveTarget(project) as? XMakeBuildProfileExecutionTarget)?.profileId
            e.presentation.icon =
                if (activeProfileId == target.profileId) AllIcons.Actions.Checked else null
        }

        override fun actionPerformed(e: AnActionEvent) {
            // Re-resolve by id: the captured instance may be stale.
            val configuration = selectedConfiguration(project) ?: return
            if (configuration !is XMakeProfileRunConfiguration) return
            val current = targetsFor(project, configuration).firstOrNull { it.profileId == target.profileId } ?: return
            ExecutionTargetManager.setActiveTarget(project, current)
        }
    }

    private class SelectDebugProfileAction(
        private val project: Project,
        private val profile: CidrDebugProfile<*>,
    ) : AnAction(profile.name) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            // Id, not instance: profile instances are rebuilt per query.
            e.presentation.icon =
                if (currentDebugProfile(project)?.id == profile.id) AllIcons.Actions.Checked else null
        }

        override fun actionPerformed(e: AnActionEvent) {
            selectDebugProfile(project, profile)
        }
    }

    private class EditProfilesAction(
        section: String,
        private val configurable: String,
    ) : AnAction("Edit $section…") {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            ShowSettingsUtil.getInstance().showSettingsDialog(project, configurable)
        }
    }

}

// File-private helpers: the action class is a plugin.xml extension, whose companion
// may hold only a logger and constants.
private val Log = logger<XMakeProfileSwitcherAction>()

private fun selectedConfiguration(project: Project): RunConfiguration? =
    RunManager.getInstance(project).selectedConfiguration?.configuration

private fun debugProfiles(project: Project): List<CidrDebugProfile<*>> =
    runCatching { CidrDebugProfileManager.getInstance().getDebugProfiles(project) }
        .onFailure { error -> Log.warn("CLion debug profiles are unavailable", error) }
        .getOrDefault(emptyList())

private fun currentDebugProfile(project: Project): CidrDebugProfile<*>? =
    runCatching { CidrDebugProfileManager.getInstance().getCurrentDebugProfile(project) }.getOrNull()

private fun selectDebugProfile(project: Project, profile: CidrDebugProfile<*>) {
    runCatching { CidrDebugProfileManager.getInstance().setCurrentDebugProfile(project, profile.id) }
        .onFailure { error -> Log.warn("Failed to select debug profile '${profile.name}'", error) }
}

private fun targetsFor(project: Project, configuration: RunConfiguration): List<XMakeBuildProfileExecutionTarget> =
    ExecutionTargetManager.getInstance(project).getTargetsFor(configuration)
        .filterIsInstance<XMakeBuildProfileExecutionTarget>()

private const val XMAKE_SECTION = "XMake Profiles"
private const val DEBUG_SECTION = "Debug Profiles"
private const val EDIT_DEBUG_PROFILES = "Edit $DEBUG_SECTION…"
private const val XMAKE_CONFIGURABLE = "XMake Profiles"
private const val DEBUG_CONFIGURABLE = "Debug Profiles"
