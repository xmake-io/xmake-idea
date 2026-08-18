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
package io.xmake.run

import com.intellij.execution.ExecutionTargetListener
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.EditorTextField
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.xmake.debug.DapDriverDetector
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.XMakeBuildProfileManager
import io.xmake.project.profile.xmakeBuildProfiles
import io.xmake.project.target.discoverXMakeBuildTargets
import io.xmake.run.command.DEFAULT_BUILD_TARGET
import io.xmake.utils.ui.LiveModelComboBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

@OptIn(ExperimentalCoroutinesApi::class)
class XMakeRunConfigurationEditor(
    private val project: Project,
) : SettingsEditor<XMakeRunConfiguration>() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val buildTargetModel = DefaultComboBoxModel<String>()
    private val buildTargetComboBox = LiveModelComboBox(buildTargetModel)
    private val runArguments = RawCommandLineEditor()
    private val environmentVariables = EnvironmentVariablesComponent(project)

    private val dapDriverAutoDetect = JBCheckBox("Auto-detect DAP driver")
    private val dapDriverPath = TextFieldWithBrowseButton().apply {
        val descriptor = FileChooserDescriptorFactory.singleFile().apply {
            title = "Select DAP Driver"
            description = "Select the lldb-dap or gdb executable"
        }
        addBrowseFolderListener(TextBrowseFolderListener(descriptor, project))
    }
    private val launchConfiguration = EditorTextField().apply {
        setOneLineMode(false)
        preferredSize = Dimension(400, 140)
        addSettingsProvider { editor ->
            editor.settings.apply {
                isFoldingOutlineShown = false
                isLineNumbersShown = false
                isCaretRowShown = true
                isAllowSingleLogicalLineFolding = false
                isDndEnabled = false
                isAnimatedScrolling = true
            }
        }
    }
    private val scrollableLaunchConfiguration: JComponent = JBScrollPane(launchConfiguration).apply {
        preferredSize = Dimension(400, 140)
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    }

    private var buildTargetProfileSnapshot: XMakeBuildProfile? = null
    private val targetRequests = Channel<XMakeBuildProfile>(Channel.CONFLATED)
    private var editedConfiguration: XMakeRunConfiguration? = null
    private var isResetting = false
    private val profileSelectionConnection = project.messageBus.connect(this)

    init {
        scope.launch {
            targetRequests.consumeAsFlow()
                .transformLatest { profile ->
                    try {
                        emit(profile to project.discoverXMakeBuildTargets(profile))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // Keep the persisted target when profile discovery is unavailable.
                        Log.warn("Failed to load XMake targets for profile ${profile.id}", error)
                    }
                }
                .collect { (requestedProfile, buildTargets) ->
                    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                        if (project.isDisposed || buildTargetProfileSnapshot != requestedProfile) {
                            return@withContext
                        }
                        replaceBuildTargetChoices(
                            buildTargetModel.selectedItem?.toString() ?: DEFAULT_BUILD_TARGET,
                            buildTargets,
                        )
                    }
                }
        }
        replaceBuildTargetChoices(DEFAULT_BUILD_TARGET, emptyList())
        profileSelectionConnection.subscribe(ExecutionTargetManager.TOPIC, ExecutionTargetListener {
            editedConfiguration?.let(::refreshBuildTargets)
        })
        profileSelectionConnection.subscribe(XMakeBuildProfileManager.TOPIC, XMakeBuildProfileManager.Listener {
            editedConfiguration?.let(::refreshBuildTargets)
        })
        dapDriverAutoDetect.addItemListener { event ->
            if (event.stateChange != ItemEvent.SELECTED && event.stateChange != ItemEvent.DESELECTED) {
                return@addItemListener
            }
            dapDriverPath.isEnabled = !dapDriverAutoDetect.isSelected
            if (!isResetting && dapDriverAutoDetect.isSelected) {
                DapDriverDetector.findBestDriver()?.let { driver ->
                    replaceDefaultLaunchConfiguration(driver.type.displayName)
                }
            }
        }
    }

    override fun resetEditorFrom(configuration: XMakeRunConfiguration) {
        editedConfiguration = configuration
        isResetting = true
        try {
            val selectedBuildTarget = configuration.runTarget.ifBlank { DEFAULT_BUILD_TARGET }
            if (buildTargetModel.getIndexOf(selectedBuildTarget) < 0) {
                buildTargetModel.addElement(selectedBuildTarget)
            }
            buildTargetModel.selectedItem = selectedBuildTarget
            runArguments.text = configuration.runArguments
            environmentVariables.envData = configuration.runEnvironment
            dapDriverAutoDetect.isSelected = configuration.dapDriverAutoDetect
            dapDriverPath.text = configuration.dapDriverPath
            launchConfiguration.text = configuration.launchConfiguration.ifBlank {
                XMakeRunConfiguration.getDefaultLaunchConfigJson()
            }
            dapDriverPath.isEnabled = !dapDriverAutoDetect.isSelected
        } finally {
            isResetting = false
        }
        refreshBuildTargets(configuration)
    }

    override fun applyEditorTo(configuration: XMakeRunConfiguration) {
        configuration.runTarget = buildTargetModel.selectedItem?.toString() ?: DEFAULT_BUILD_TARGET
        configuration.runArguments = runArguments.text
        configuration.runEnvironment = environmentVariables.envData
        configuration.dapDriverAutoDetect = dapDriverAutoDetect.isSelected
        configuration.dapDriverPath = dapDriverPath.text
        configuration.launchConfiguration = launchConfiguration.text
    }

    override fun createEditor(): JComponent = panel {
        row("Target:") {
            cell(buildTargetComboBox).align(AlignX.FILL)
        }

        row("Program arguments:") {
            cell(runArguments).align(AlignX.FILL)
        }

        row("Environment variables:") {
            cell(environmentVariables.component).align(AlignX.FILL)
        }

        collapsibleGroup("Debug Configuration") {
            row {
                cell(dapDriverAutoDetect)
            }
            row("DAP driver:") {
                cell(dapDriverPath).align(AlignX.FILL)
            }
            row("Launch configuration:") {
                cell(scrollableLaunchConfiguration).align(AlignX.FILL)
            }
        }
    }

    override fun disposeEditor() {
        scope.cancel()
        super.disposeEditor()
    }

    private fun refreshBuildTargets(configuration: XMakeRunConfiguration) {
        val profile = configuration.preferredBuildProfileId
            ?.let(project.xmakeBuildProfiles::findProfile)
            ?: project.xmakeBuildProfiles.profiles.singleOrNull()
        if (profile == null) {
            resetBuildTargetChoices(configuration.runTarget)
        } else {
            requestBuildTargets(profile)
        }
    }

    private fun requestBuildTargets(profile: XMakeBuildProfile) {
        val requestedProfileSnapshot = profile.copy()
        val selectedBuildTarget = buildTargetModel.selectedItem?.toString() ?: DEFAULT_BUILD_TARGET
        if (buildTargetProfileSnapshot != requestedProfileSnapshot) {
            buildTargetProfileSnapshot = requestedProfileSnapshot
            replaceBuildTargetChoices(selectedBuildTarget, emptyList())
        }
        targetRequests.trySend(requestedProfileSnapshot)
    }

    private fun resetBuildTargetChoices(selectedTarget: String) {
        buildTargetProfileSnapshot = null
        replaceBuildTargetChoices(selectedTarget, emptyList())
    }

    private fun replaceBuildTargetChoices(selectedTarget: String, buildTargets: Iterable<String>) {
        val selectedBuildTarget = selectedTarget.ifBlank { DEFAULT_BUILD_TARGET }
        val buildTargetChoices = buildList {
            add(DEFAULT_BUILD_TARGET)
            addAll(buildTargets.filter(String::isNotBlank))
            // Keep the persisted/custom target even when discovery does not return it.
            add(selectedBuildTarget)
        }.distinct()

        buildTargetModel.removeAllElements()
        buildTargetModel.addAll(buildTargetChoices)
        buildTargetModel.selectedItem = selectedBuildTarget
        buildTargetComboBox.refreshPopupFromModel()
    }

    private fun replaceDefaultLaunchConfiguration(driverName: String) {
        val current = launchConfiguration.text.trim()
        val gdbDefault = XMakeRunConfiguration.getDefaultGdbLaunchConfigJson()
        val lldbDefault = XMakeRunConfiguration.getDefaultLldbLaunchConfigJson()
        if (current.isNotEmpty() && current != gdbDefault.trim() && current != lldbDefault.trim()) return

        launchConfiguration.text = if (driverName.contains("gdb", ignoreCase = true)) {
            gdbDefault
        } else {
            lldbDefault
        }
    }

    private companion object {
        val Log = logger<XMakeRunConfigurationEditor>()
    }
}
