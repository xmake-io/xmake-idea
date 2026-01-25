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
 * @file        XMakeRunConfigurationEditor.kt
 *
 */
package io.xmake.run

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.messages.MessageBusConnection
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.EditorTextField
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.CheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComboBoxPredicate
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.target.TargetManager
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ui.ToolkitComboBox
import io.xmake.project.toolkit.ui.ToolkitListItem
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.execute.SyncDirection
import io.xmake.utils.execute.transferFolderByToolkit
import io.xmake.utils.info.XMakeInfo
import io.xmake.debug.DapDriverDetector
import io.xmake.utils.info.XMakeInfoManager
import io.xmake.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.event.PopupMenuEvent

class XMakeRunConfigurationEditor(
    private val project: Project,
    private val runConfiguration: XMakeRunConfiguration,
) : SettingsEditor<XMakeRunConfiguration>() {

    private val scope = CoroutineScope(Dispatchers.Default)

    private var messageBusConnection: MessageBusConnection? = null

    init {
        messageBusConnection = project.messageBus.connect()
        messageBusConnection!!.subscribe(XMakeInfoManager.XMAKE_INFO_TOPIC, object : XMakeInfoManager.XMakeInfoListener {
            override fun onXMakeInfoUpdated(xmakeInfo: XMakeInfo) {
                SwingUtilities.invokeLater {
                    updateComboBoxes()
                }
            }
        })

        // Try to update combo boxes initially if info is already available
        SwingUtilities.invokeLater {
            updateComboBoxes()
        }
    }

    override fun disposeEditor() {
        messageBusConnection?.disconnect()
        super.disposeEditor()
    }

    private fun updateComboBoxes() {

        val xmakeInfo = XMakeInfoManager.getInstance(project).xmakeInfo

        val selectedPlatform = platformsComboBox.item
        platformsModel.removeAllElements()
        val platforms = if (xmakeInfo.platforms.isNotEmpty()) {
            xmakeInfo.platforms.plus("default")
        } else {
            xmakeInfo.architectures.keys.plus("default")
        }.toList()
        platformsModel.addAll(platforms)
        platformsComboBox.item = if (platforms.contains(selectedPlatform)) selectedPlatform else runConfiguration.runPlatform

        val selectedArch = architecturesComboBox.item
        architecturesModel.removeAllElements()
        val currentPlatform = platformsComboBox.item as? String ?: "default"
        val architectures = (xmakeInfo.architectures[currentPlatform] ?: emptyList()).plus("default")
        architecturesModel.addAll(architectures)
        architecturesComboBox.item = if (architectures.contains(selectedArch)) selectedArch else runConfiguration.runArchitecture

        val selectedToolchain = toolchainsComboBox.item
        toolchainsModel.removeAllElements()
        val toolchains = xmakeInfo.toolchains.keys.plus("default").toList()
        toolchainsModel.addAll(toolchains)
        toolchainsComboBox.item = if (toolchains.contains(selectedToolchain)) selectedToolchain else runConfiguration.runToolchain

        val selectedMode = modesComboBox.item
        modesModel.removeAllElements()
        val modes = if (xmakeInfo.buildModes.isNotEmpty()) {
            xmakeInfo.buildModes.map { it.removePrefix("mode.") }.toList()
        } else {
            listOf("release", "debug")
        }
        modesModel.addAll(modes)
        modesComboBox.item = if (modes.contains(selectedMode)) selectedMode else runConfiguration.runMode

        val selectedTarget = targetsModel.selectedItem
        targetsModel.removeAllElements()
        val targets = if (xmakeInfo.targets.isNotEmpty()) {
            xmakeInfo.targets.plus("default")
        } else {
            (runConfiguration.runToolkit?.let {
                TargetManager.getInstance(project).detectXMakeTarget(it, runConfiguration.runWorkingDir)
            } ?: emptyList()).plus("default")
        }.distinct().toList()
        targetsModel.addAll(targets)
        targetsModel.selectedItem = if (targets.contains(selectedTarget)) selectedTarget else runConfiguration.runTarget
    }

    private fun updateDapDriverComboBox() {
        val availableDrivers = runConfiguration.getAvailableDapDrivers()
        dapDriverPathComboBox.removeAllItems()
        
        if (availableDrivers.isEmpty()) {
            dapDriverPathComboBox.addItem("No DAP drivers found")
        } else {
            availableDrivers.forEach { driver ->
                dapDriverPathComboBox.addItem("${driver.displayName} - ${driver.path}")
            }
        }
        
        // Set current selection
        val currentPath = runConfiguration.getEffectiveDapDriverPath()
        if (currentPath.isNotBlank()) {
            val currentIndex = availableDrivers.indexOfFirst { it.path == currentPath }
            if (currentIndex >= 0) {
                dapDriverPathComboBox.selectedIndex = currentIndex
            }
        }
    }

    private var toolkit: Toolkit? = runConfiguration.runToolkit
    private val toolkitComboBox = ToolkitComboBox(::toolkit)

    // the targets ui
    private val targetsModel = DefaultComboBoxModel<String>()
    private val targetsComboBox = ComboBox(targetsModel).apply { item = runConfiguration.runTarget }

    private val platformsModel = DefaultComboBoxModel(runConfiguration.platforms)
    private val platformsComboBox = ComboBox(platformsModel).apply { item = runConfiguration.runPlatform }

    private val architecturesModel =
        DefaultComboBoxModel(runConfiguration.getArchitecturesByPlatform(runConfiguration.runPlatform))
    private val architecturesComboBox = ComboBox(architecturesModel).apply { item = runConfiguration.runArchitecture }

    private val toolchainsModel = DefaultComboBoxModel(runConfiguration.toolchains)
    private val toolchainsComboBox = ComboBox(toolchainsModel).apply { item = runConfiguration.runToolchain }

    private val modesModel = DefaultComboBoxModel(runConfiguration.modes)
    private val modesComboBox = ComboBox(modesModel).apply { item = runConfiguration.runMode }

    private val runArguments = RawCommandLineEditor()

    private val environmentVariables = EnvironmentVariablesTextFieldWithBrowseButton()

    private val workingDirectoryBrowser = DirectoryBrowser(project).apply { text = runConfiguration.runWorkingDir }

    private val buildDirectoryBrowser = DirectoryBrowser(project).apply { text = runConfiguration.buildDirectory }

    private val androidNDKDirectoryBrowser =
        DirectoryBrowser(project).apply { text = runConfiguration.androidNDKDirectory }

    private var enableVerbose: Boolean = runConfiguration.enableVerbose

    private val enableVerboseCheckBox = CheckBox("Enable verbose output", enableVerbose)

    private val additionalConfiguration = RawCommandLineEditor()

    // Launch configuration for debugging (JSON format)
    private val launchConfiguration = EditorTextField(XMakeRunConfiguration.getDefaultLaunchConfigJson()).apply {
        // Set up for multi-line JSON editing
        setOneLineMode(false)
        preferredSize = java.awt.Dimension(400, 120)
        
        // Configure editor settings when editor is created
        editor?.let { editor ->
            val settings = editor.settings
            settings.isFoldingOutlineShown = false
            settings.isLineNumbersShown = false
            settings.isCaretRowShown = true
            settings.isAllowSingleLogicalLineFolding = false
            settings.isDndEnabled = false
            // Enable scrolling
            settings.isAnimatedScrolling = true
        }
    }
    
    // Create scrollable wrapper for the editor
    private val scrollableLaunchConfiguration: JComponent = JBScrollPane(launchConfiguration).apply {
        preferredSize = java.awt.Dimension(400, 120)
        verticalScrollBarPolicy = javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    }

    // DAP driver configuration UI components
    private val dapDriverAutoDetectCheckBox = CheckBox("Auto-detect DAP driver", runConfiguration.dapDriverAutoDetect)
    
    private val dapDriverPathComboBox = ComboBox<String>()
    private val dapDriverPathCustomField = TextFieldWithBrowseButton().apply {
        textField.isEditable = true
        addBrowseFolderListener(
            "Select DAP Driver",
            "Select the DAP driver executable (lldb-dap or gdb)",
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
    }

    // reset editor from configuration
    override fun resetEditorFrom(configuration: XMakeRunConfiguration) {

        if (configuration.runToolkit != null) {
            toolkitComboBox.selectToolkit(configuration.runToolkit)
        }

        // Update combo boxes data from XMakeInfo first
        updateComboBoxes()

        // reset targets
        targetsModel.selectedItem = configuration.runTarget

        platformsComboBox.item = configuration.runPlatform

        architecturesComboBox.item = configuration.runArchitecture

        toolchainsComboBox.item = configuration.runToolchain

        modesComboBox.item = configuration.runMode

        // reset run arguments
        runArguments.text = configuration.runArguments

        // reset environment variables
        environmentVariables.data = configuration.runEnvironment

        workingDirectoryBrowser.text = configuration.runWorkingDir

        buildDirectoryBrowser.text = configuration.buildDirectory

        androidNDKDirectoryBrowser.text = configuration.androidNDKDirectory

        enableVerbose = configuration.enableVerbose
        enableVerboseCheckBox.setSelected(enableVerbose)

        additionalConfiguration.text = configuration.additionalConfiguration

        launchConfiguration.text = if (configuration.launchConfiguration.isBlank()) {
            XMakeRunConfiguration.getDefaultLaunchConfigJson()
        } else {
            configuration.launchConfiguration
        }

        // reset DAP driver configuration
        dapDriverAutoDetectCheckBox.isSelected = configuration.dapDriverAutoDetect
        updateDapDriverComboBox()
        dapDriverPathCustomField.text = configuration.dapDriverPath
        
        // Set initial enabled state based on auto-detect setting
        val isAutoDetect = dapDriverAutoDetectCheckBox.isSelected
        dapDriverPathComboBox.isEnabled = !isAutoDetect
        dapDriverPathCustomField.isEnabled = !isAutoDetect
        
        // Initial update of launch configuration based on effective driver
        val effectivePath = configuration.getEffectiveDapDriverPath()
        // If the configuration is blank, we must initialize it with the correct default
        // based on the effective driver (which might be GDB or LLDB).
        // If it's not blank, we leave it alone (it's user's saved state).
        if (launchConfiguration.text.isBlank() && effectivePath.isNotBlank()) {
            val availableDrivers = configuration.getAvailableDapDrivers()
            val driver = availableDrivers.find { it.path == effectivePath }
            if (driver != null) {
                 if (driver.type.displayName.contains("gdb", ignoreCase = true)) {
                    launchConfiguration.text = XMakeRunConfiguration.getDefaultGdbLaunchConfigJson()
                } else {
                    launchConfiguration.text = XMakeRunConfiguration.getDefaultLldbLaunchConfigJson()
                }
            }
        }
        
        // Add DAP driver checkbox listener
        dapDriverAutoDetectCheckBox.addItemListener {
            val isAutoDetect = dapDriverAutoDetectCheckBox.isSelected
            dapDriverPathComboBox.isEnabled = !isAutoDetect
            dapDriverPathCustomField.isEnabled = !isAutoDetect

            if (isAutoDetect) {
                // If auto-detect is enabled, find the best driver and update config
                val bestDriver = io.xmake.debug.DapDriverDetector.findBestDriver()
                if (bestDriver != null) {
                    updateLaunchConfigurationForDriver(bestDriver.type.displayName)
                }
            }
        }
        
        // Add DAP driver combo box listener
        dapDriverPathComboBox.addItemListener {
            if (it.stateChange == java.awt.event.ItemEvent.SELECTED) {
                val selectedDriver = runConfiguration.getAvailableDapDrivers().getOrNull(dapDriverPathComboBox.selectedIndex)
                if (selectedDriver != null) {
                    dapDriverPathCustomField.text = selectedDriver.path
                    if (!dapDriverAutoDetectCheckBox.isSelected) {
                        updateLaunchConfigurationForDriver(selectedDriver.type.displayName)
                    }
                }
            }
        }
    }

    // apply editor to configuration
    override fun applyEditorTo(configuration: XMakeRunConfiguration) {

        configuration.runToolkit = toolkit

        configuration.runTarget = (targetsModel.selectedItem ?: "default").toString()

        configuration.runPlatform = platformsComboBox.item ?: "default"

        configuration.runArchitecture = architecturesComboBox.item ?: "default"

        configuration.runToolchain = toolchainsComboBox.item ?: "default"

        configuration.runMode = modesComboBox.item ?: "default"

        configuration.runArguments = runArguments.text

        configuration.runEnvironment = environmentVariables.data

        configuration.runWorkingDir = workingDirectoryBrowser.text

        configuration.buildDirectory = buildDirectoryBrowser.text

        configuration.androidNDKDirectory = androidNDKDirectoryBrowser.text

        enableVerbose = enableVerboseCheckBox.isSelected()
        configuration.enableVerbose = enableVerbose

        configuration.additionalConfiguration = additionalConfiguration.text

        configuration.launchConfiguration = launchConfiguration.text

        // apply DAP driver configuration
        configuration.dapDriverAutoDetect = dapDriverAutoDetectCheckBox.isSelected
        configuration.dapDriverPath = dapDriverPathCustomField.text

        project.xmakeConfiguration.changed = true
    }

    // create editor
    override fun createEditor(): JComponent = panel {

        row("Xmake Toolkit:") {
            cell(toolkitComboBox).align(AlignX.FILL).applyToComponent {
                // Todo: Store previously selected toolkit to restore it if not applied.
                addToolkitChangedListener { toolkit ->
                    workingDirectoryBrowser.removeBrowserAllListener()
                    buildDirectoryBrowser.removeBrowserAllListener()
                    androidNDKDirectoryBrowser.removeBrowserAllListener()
                    toolkit?.let {
                        workingDirectoryBrowser.addBrowserListenerByToolkit(it)
                        buildDirectoryBrowser.addBrowserListenerByToolkit(it)
                        androidNDKDirectoryBrowser.addBrowserListenerByToolkit(it)
                        XMakeInfoManager.getInstance(project).probeXMakeInfo(it)
                    }
                }
                activatedToolkit?.let {
                    workingDirectoryBrowser.addBrowserListenerByToolkit(it)
                    buildDirectoryBrowser.addBrowserListenerByToolkit(it)
                    androidNDKDirectoryBrowser.addBrowserListenerByToolkit(it)
                    XMakeInfoManager.getInstance(project).probeXMakeInfo(it)
                }
            }
        }

        row {
            label("Configuration:").align(AlignY.TOP)
            panel {
                row {
                    label("Platform:")
                }
                row {
                    cell(platformsComboBox).applyToComponent {
                        addItemListener {
                            val selected = selectedItem
                            if (selected is String) {
                                val architectures = runConfiguration.getArchitecturesByPlatform(selected)
                                with(architecturesModel) {
                                    removeAllElements()
                                    addAll(architectures.toMutableList())
                                    if (architectures.isNotEmpty()) {
                                        selectedItem = architectures.first()
                                    }
                                }
                            }
                        }
                    }.align(AlignX.FILL)
                }
            }.resizableColumn()
            panel {
                row {
                    label("Architecture:")
                }
                row {
                    cell(architecturesComboBox).align(AlignX.FILL)
                }
            }.resizableColumn()
            panel {
                row {
                    label("Toolchain:")
                }
                row {
                    cell(toolchainsComboBox).align(AlignX.FILL)
                }
            }.resizableColumn()
        }.layout(RowLayout.PARENT_GRID)

        separator()

        row("Target:") {
            cell(targetsComboBox).align(AlignX.FILL).resizableColumn()
            label("Mode:").align(AlignX.FILL)
            cell(modesComboBox).align(AlignX.FILL)
        }

        row("Program Arguments:") {
            cell(runArguments).align(AlignX.FILL)
        }

        // Debug Configuration
        collapsibleGroup("Debug Configuration") {
            row("") {
                cell(dapDriverAutoDetectCheckBox)
            }
            
            row("DAP Driver:") {
                cell(dapDriverPathComboBox).align(AlignX.FILL).resizableColumn()
            }
            
            row("Custom DAP Driver Path:") {
                cell(dapDriverPathCustomField).align(AlignX.FILL)
            }
            
            row {
                label("Launch Configuration (JSON format):")
            }
            row {
                cell(scrollableLaunchConfiguration).align(AlignX.FILL).resizableColumn()
            }
            row {
                comment("Override default debug settings with JSON configuration")
            }
        }

        collapsibleGroup("Additional Configuration") {
            row("Environment variables") {
                cell(environmentVariables).align(AlignX.FILL)
            }

            row("Working directory") {
                cell(workingDirectoryBrowser).align(AlignX.FILL)
            }

            row("Build directory") {
                cell(buildDirectoryBrowser).align(AlignX.FILL)
            }

            row("Android NDK directory") {
                cell(androidNDKDirectoryBrowser).align(AlignX.FILL)
            }

            row("Additional Configuration") {
                cell(additionalConfiguration).align(AlignX.FILL)
            }

            row("") {
                cell(enableVerboseCheckBox)
            }
        }


        row("Sync Directory:") {
            button("Upload") {
                toolkitComboBox.activatedToolkit?.let { toolkit ->
                    val workingDirectoryPath = workingDirectoryBrowser.text

                    scope.launch(Dispatchers.IO) {
                        if (toolkit.isOnRemote) {
                            transferFolderByToolkit(
                                project,
                                toolkit,
                                SyncDirection.UPSTREAM_TO_LOCAL,
                                workingDirectoryPath,
                                null
                            )
                        }
                    }
                }
            }
        }.visibleIf(ComboBoxPredicate<ToolkitListItem>(toolkitComboBox) {
            val toolkit = (it as? ToolkitListItem.ToolkitItem)?.toolkit
            toolkit?.isOnRemote ?: false
        })
    }

    private fun updateLaunchConfigurationForDriver(driverName: String) {
        if (driverName.contains("gdb", ignoreCase = true)) {
            launchConfiguration.text = XMakeRunConfiguration.getDefaultGdbLaunchConfigJson()
        } else {
            launchConfiguration.text = XMakeRunConfiguration.getDefaultLldbLaunchConfigJson()
        }
    }

    private fun JPanel.makeWide() {
        preferredSize = Dimension(1000, height)
    }
}
