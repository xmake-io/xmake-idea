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
 * @file        XMakeSelectorAction.kt
 *
 */
package io.xmake.actions.selector

import com.intellij.execution.RunManager
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.project.Project
import io.xmake.project.xmakeSettings
import io.xmake.run.XMakeRunConfiguration
import io.xmake.shared.xmakeConfigurationOrNull
import io.xmake.utils.SystemUtils
import io.xmake.utils.exception.XMakeRunConfigurationNotSetException
import javax.swing.JComponent

/**
 * Base for the Rider-style toolbar dropdowns that quickly edit the two most-used fields of the
 * currently selected [XMakeRunConfiguration] (build mode and target). The run configuration stays
 * the persistence layer, so command building in `XMakeConfiguration` is untouched.
 */
abstract class XMakeSelectorAction : ComboBoxAction() {

    /** The value currently shown on the widget. */
    protected abstract fun currentValue(config: XMakeRunConfiguration): String

    /** The options to offer in the dropdown. */
    protected abstract fun options(project: Project, config: XMakeRunConfiguration): List<String>

    /** Writes the chosen value back onto the run configuration. */
    protected abstract fun applyValue(config: XMakeRunConfiguration, value: String)

    /**
     * Whether changing this selector requires re-running `xmake f`. Only mode/platform/arch/
     * toolchain are configure-time options in xmake; the target is chosen at build/run time, so
     * the target selector overrides this to `false`.
     */
    protected open val requiresReconfigure: Boolean = true

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    protected fun selectedConfig(project: Project?): XMakeRunConfiguration? {
        project ?: return null
        return RunManager.getInstance(project).selectedConfiguration?.configuration as? XMakeRunConfiguration
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val config = selectedConfig(project)
        val presentation = e.presentation
        if (project == null || !SystemUtils.isXMakeProject(project) || config == null) {
            presentation.isEnabledAndVisible = false
            return
        }
        presentation.isEnabledAndVisible = true
        presentation.text = currentValue(config)
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return group
        val config = selectedConfig(project) ?: return group
        options(project, config).distinct().forEach { value ->
            group.add(object : AnAction(value) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (currentValue(config) != value) {
                        applyValue(config, value)
                        onSelectionChanged(project, requiresReconfigure)
                    }
                }
            })
        }
        return group
    }

    /**
     * Called after the user picks a new value. For target changes ([reconfigure] `false`) the new
     * value is already stored on the run config and nothing else is needed. For configure-time
     * changes it marks the configuration dirty and, when enabled in settings, re-runs `xmake f`.
     */
    protected fun onSelectionChanged(project: Project, reconfigure: Boolean) {
        if (!reconfigure) return
        val xmakeConfiguration = project.xmakeConfigurationOrNull ?: return
        xmakeConfiguration.changed = true
        if (!project.xmakeSettings.state.autoReloadConfigOnSwitch) return
        try {
            SystemUtils.runvInConsole(project, xmakeConfiguration.configurationCommandLine)
                ?.addProcessListener(object : ProcessListener {
                    override fun processTerminated(e: ProcessEvent) {
                        if (e.exitCode == 0 && project.xmakeSettings.state.autoUpdateCompileCommands) {
                            SystemUtils.runvInConsole(
                                project, xmakeConfiguration.updateCompileCommandsLine, false, true, true
                            )
                        }
                    }
                })
            xmakeConfiguration.changed = false
        } catch (ex: XMakeRunConfigurationNotSetException) {
            // No selected xmake run configuration; nothing to reload.
        }
    }
}
