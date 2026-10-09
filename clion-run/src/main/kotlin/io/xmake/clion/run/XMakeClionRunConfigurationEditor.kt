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
package io.xmake.clion.run

import com.intellij.execution.ui.CommonProgramParametersPanel
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.xmake.run.XMakeBuildTargetSelector
import javax.swing.JComponent

internal class XMakeClionRunConfigurationEditor(
    project: Project,
) : SettingsEditor<XMakeClionRunConfiguration>() {

    private val targetSelector = XMakeBuildTargetSelector(project, this)
    private val common = CommonProgramParametersPanel()

    override fun resetEditorFrom(configuration: XMakeClionRunConfiguration) {
        targetSelector.reset(configuration)
        common.reset(configuration)
    }

    override fun applyEditorTo(configuration: XMakeClionRunConfiguration) {
        configuration.runTarget = targetSelector.selectedTarget
        common.applyTo(configuration)
    }

    override fun createEditor(): JComponent = panel {
        row("Target:") {
            cell(targetSelector.component).align(AlignX.FILL)
        }
        row {
            cell(common).align(AlignX.FILL)
        }
    }
}
