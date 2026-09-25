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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.xmake.clion.XMakeClionLaunchBridge
import io.xmake.utils.ui.LiveModelComboBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

/** The xmake target (discovered from the profile, still editable) plus CLion's program settings. */
internal class XMakeClionRunConfigurationEditor(
    private val project: Project,
) : SettingsEditor<XMakeClionRunConfiguration>() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val targetModel = DefaultComboBoxModel<String>()
    private val target = LiveModelComboBox(targetModel).apply { isEditable = true }
    private val common = CommonProgramParametersPanel()

    init {
        Disposer.register(this, target)
    }

    override fun resetEditorFrom(configuration: XMakeClionRunConfiguration) {
        setTargetChoices(configuration.runTarget, emptyList())
        common.reset(configuration)
        val profileId = configuration.preferredBuildProfileId
        scope.launch {
            val discovered = try {
                XMakeClionLaunchBridge.discoverTargets(project, profileId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Keep whatever target is already set when discovery is unavailable.
                LOG.warn("Failed to discover XMake targets", error)
                return@launch
            }
            withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                setTargetChoices(currentTarget(), discovered)
            }
        }
    }

    override fun applyEditorTo(configuration: XMakeClionRunConfiguration) {
        configuration.runTarget = currentTarget()
        common.applyTo(configuration)
    }

    override fun createEditor(): JComponent = panel {
        row("XMake target:") {
            cell(target).align(AlignX.FILL)
        }
        row {
            cell(common).align(AlignX.FILL)
        }
    }

    override fun disposeEditor() {
        scope.cancel()
        super.disposeEditor()
    }

    private fun currentTarget(): String =
        (target.editor.item ?: target.selectedItem)?.toString()?.trim().orEmpty()

    private fun setTargetChoices(selected: String, discovered: List<String>) {
        val choices = (discovered + selected).filter(String::isNotBlank).distinct()
        targetModel.removeAllElements()
        targetModel.addAll(choices)
        targetModel.selectedItem = selected.ifBlank { discovered.firstOrNull() }
        target.refreshPopupFromModel()
    }

    private companion object {
        val LOG = logger<XMakeClionRunConfigurationEditor>()
    }
}
