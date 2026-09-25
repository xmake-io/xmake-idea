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
import io.xmake.project.directory.XMakeProjectDirectoryManager
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.profile.XMakeBuildProfileManager
import io.xmake.project.profile.xmakeBuildProfiles
import java.awt.Dimension
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

class XMakeRunConfigurationEditor(
    private val project: Project,
) : SettingsEditor<XMakeRunConfiguration>() {

    private val targetSelector = XMakeBuildTargetSelector(project, this)
    private val runArguments = RawCommandLineEditor()
    private val environmentVariables = EnvironmentVariablesComponent(project)
    private val launchWorkingDirectory = DirectoryBrowser(
        project,
        browseTitle = "Working Directory",
        browseDescription = "Select the target process working directory",
    )

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

    private var editedConfiguration: XMakeRunConfiguration? = null
    private var isResetting = false
    private val profileSelectionConnection = project.messageBus.connect(this)

    init {
        profileSelectionConnection.subscribe(ExecutionTargetManager.TOPIC, ExecutionTargetListener {
            editedConfiguration?.let(::refreshWorkingDirectoryToolkit)
        })
        profileSelectionConnection.subscribe(XMakeBuildProfileManager.TOPIC, XMakeBuildProfileManager.Listener {
            editedConfiguration?.let(::refreshWorkingDirectoryToolkit)
        })
        profileSelectionConnection.subscribe(XMakeProjectDirectoryManager.TOPIC, XMakeProjectDirectoryManager.Listener {
            editedConfiguration?.let(::refreshWorkingDirectoryToolkit)
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
            runArguments.text = configuration.runArguments
            launchWorkingDirectory.text = configuration.launchWorkingDirectory
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
        targetSelector.reset(configuration)
        refreshWorkingDirectoryToolkit(configuration)
    }

    override fun applyEditorTo(configuration: XMakeRunConfiguration) {
        configuration.runTarget = targetSelector.selectedTarget
        configuration.runArguments = runArguments.text
        configuration.launchWorkingDirectory = launchWorkingDirectory.text
        configuration.runEnvironment = environmentVariables.envData
        configuration.dapDriverAutoDetect = dapDriverAutoDetect.isSelected
        configuration.dapDriverPath = dapDriverPath.text
        configuration.launchConfiguration = launchConfiguration.text
    }

    override fun createEditor(): JComponent = panel {
        row("Target:") {
            cell(targetSelector.component).align(AlignX.FILL)
        }

        row("Program arguments:") {
            cell(runArguments).align(AlignX.FILL)
        }

        row("Working directory:") {
            cell(launchWorkingDirectory).align(AlignX.FILL)
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

    private fun refreshWorkingDirectoryToolkit(configuration: XMakeRunConfiguration) {
        val profile = configuration.preferredBuildProfileId
            ?.let(project.xmakeBuildProfiles::findProfile)
            ?: project.xmakeBuildProfiles.profiles.singleOrNull()
        launchWorkingDirectory.setToolkit(profile?.resolveToolkit(project))
    }

    private fun replaceDefaultLaunchConfiguration(driverName: String) {
        val currentLaunchConfiguration = launchConfiguration.text.trim()
        val gdbDefault = XMakeRunConfiguration.getDefaultGdbLaunchConfigJson()
        val lldbDefault = XMakeRunConfiguration.getDefaultLldbLaunchConfigJson()
        if (
            currentLaunchConfiguration.isNotEmpty() &&
            currentLaunchConfiguration != gdbDefault.trim() &&
            currentLaunchConfiguration != lldbDefault.trim()
        ) return

        launchConfiguration.text = if (driverName.contains("gdb", ignoreCase = true)) {
            gdbDefault
        } else {
            lldbDefault
        }
    }
}
