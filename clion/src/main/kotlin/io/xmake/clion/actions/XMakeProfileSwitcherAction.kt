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
package io.xmake.clion.actions

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
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import io.xmake.run.XMakeProfileRunConfiguration
import io.xmake.run.XMakeRunConfigurationType
import io.xmake.run.target.XMakeBuildProfileExecutionTarget
import javax.swing.JComponent

class XMakeProfileSwitcherAction : ComboBoxAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val configuration = selectedConfiguration(project)
        e.presentation.isEnabledAndVisible =
            !project.isDisposed && isXMakeProject(project) &&
                    (configuration == null || configuration is XMakeProfileRunConfiguration)
        if (e.presentation.isEnabledAndVisible) {
            e.presentation.text = switcherText(project, configuration)
        }
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return DefaultActionGroup()
        val group = DefaultActionGroup()
        group.add(Separator.create(XMAKE_SECTION))
        when (val configuration = selectedConfiguration(project)) {
            is XMakeProfileRunConfiguration -> {
                val targets = targetsFor(project, configuration)
                if (targets.isEmpty()) {
                    group.add(SelectConfigurationHintAction())
                } else {
                    targets.forEach { group.add(SelectXMakeProfileAction(project, it)) }
                }
            }
            else -> group.add(
                if (hasXMakeConfigurations(project)) SelectConfigurationHintAction()
                else CreateXMakeConfigurationAction(),
            )
        }
        group.add(EditProfilesAction(XMAKE_SECTION, XMAKE_CONFIGURABLE))
        group.add(Separator.create(DEBUG_SECTION))
        debugProfiles(project).forEach { group.add(SelectDebugProfileAction(project, it)) }
        group.add(EditProfilesAction(DEBUG_SECTION, DEBUG_CONFIGURABLE))
        return group
    }

    private fun switcherText(project: Project, configuration: RunConfiguration?): String {
        val profile = (ExecutionTargetManager.getActiveTarget(project) as? XMakeBuildProfileExecutionTarget)
            ?.displayName
        val debugger = currentDebugProfile(project)?.name
        return when {
            configuration !is XMakeProfileRunConfiguration || profile == null -> PLACEHOLDER
            debugger == null -> profile
            else -> "$profile | $debugger"
        }
    }

    private class SelectConfigurationHintAction :
        AnAction("Select an XMake Run Configuration First", null, AllIcons.General.Information) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.isEnabled = false
        }

        override fun actionPerformed(e: AnActionEvent) = Unit
    }

    private class CreateXMakeConfigurationAction :
        AnAction("Create XMake Run Configuration…", null, AllIcons.General.Add) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            val runManager = RunManager.getInstance(e.project ?: return)
            val settings = runManager.createConfiguration("XMake", XMakeRunConfigurationType.getInstance().factory)
            runManager.addConfiguration(settings)
            runManager.selectedConfiguration = settings
        }
    }

    private class SelectXMakeProfileAction(
        private val project: Project,
        private val target: XMakeBuildProfileExecutionTarget,
    ) : AnAction(target.displayName) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            val activeId = (ExecutionTargetManager.getActiveTarget(project) as? XMakeBuildProfileExecutionTarget)?.profileId
            e.presentation.icon = AllIcons.Actions.Checked.takeIf { activeId == target.profileId }
        }

        override fun actionPerformed(e: AnActionEvent) {
            val configuration = selectedConfiguration(project) as? XMakeProfileRunConfiguration ?: return
            val current = targetsFor(project, configuration as RunConfiguration).firstOrNull { it.profileId == target.profileId } ?: return
            ExecutionTargetManager.setActiveTarget(project, current)
        }
    }

    private class SelectDebugProfileAction(
        private val project: Project,
        private val profile: CidrDebugProfile<*>,
    ) : AnAction(profile.name) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            e.presentation.icon = AllIcons.Actions.Checked.takeIf { currentDebugProfile(project)?.id == profile.id }
        }

        override fun actionPerformed(e: AnActionEvent) {
            selectDebugProfile(project, profile)
        }
    }

    private class EditProfilesAction(
        section: String,
        private val configurableId: String,
    ) : AnAction("Edit $section…") {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

        override fun actionPerformed(e: AnActionEvent) {
            ShowSettingsUtil.getInstance()
                .showSettingsDialog(e.project, { (it as? SearchableConfigurable)?.id == configurableId }, null)
        }
    }
}

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

private fun isXMakeProject(project: Project): Boolean =
    hasXMakeConfigurations(project) ||
            project.basePath?.let { LocalFileSystem.getInstance().findFileByPath("$it/xmake.lua") != null } == true

private fun hasXMakeConfigurations(project: Project): Boolean =
    RunManager.getInstance(project).getConfigurationsList(XMakeRunConfigurationType.getInstance()).isNotEmpty()

private const val PLACEHOLDER = "XMake Profiles"
private const val XMAKE_SECTION = "XMake Profiles"
private const val DEBUG_SECTION = "Debug Profiles"
private const val XMAKE_CONFIGURABLE = "XMakeBuildProfilesSettings"
private const val DEBUG_CONFIGURABLE = "cidr.DebugProfilesConfigurable"
