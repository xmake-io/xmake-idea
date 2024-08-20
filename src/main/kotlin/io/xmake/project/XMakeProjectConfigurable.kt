package io.xmake.project

import com.intellij.openapi.project.Project

@Deprecated("Migrate to Run Configuration Editor.")
class XMakeProjectConfigurable(
    private val project: Project,
)/* : Configurable, Configurable.NoScroll {
    private val platformsModel = DefaultComboBoxModel<String>()

    // the architectures ui
    private val architecturesModel = DefaultComboBoxModel<String>()

    // the modes ui
    private val modesModel = DefaultComboBoxModel<String>()

    // the additional configuration
    private val additionalConfiguration = JBTextField()

    // the configuration command text
    private val configurationCommandText = JTextArea(10, 10)

    // the android NDK directory
    private val androidNDKDirectory = run {
        val textField = TextFieldWithBrowseButton().apply {
            val fileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                title = ExecutionBundle.message("select.working.directory.message")
            }
            addBrowseFolderListener(null, null, null, fileChooser)
        }
        LabeledComponent.create(textField, ExecutionBundle.message("run.configuration.working.directory.label"))
    }

    // the build output directory
    private val buildOutputDirectory = run {
        val textField = TextFieldWithBrowseButton().apply {
            val fileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                title = ExecutionBundle.message("select.working.directory.message")
            }
            addBrowseFolderListener(null, null, null, fileChooser)
        }
        LabeledComponent.create(textField, ExecutionBundle.message("run.configuration.working.directory.label"))
    }

    // verbose output
    private var verboseOutput = JBCheckBox("Show verbose output", true)

    override fun createComponent(): JComponent {
        return panel {
            row("Platform:") {
                comboBox(platformsModel)
            }

            row("Architecture:") {
                comboBox(architecturesModel)
            }

            row("Mode:") {
                comboBox(modesModel)
            }

            row("Additional configuration:") {
                cell(additionalConfiguration).align(AlignX.FILL)
            }

            row {
                cell(verboseOutput)
            }

            row(buildOutputDirectory.label) {
                cell(buildOutputDirectory).align(AlignX.FILL)
            }
            buildOutputDirectory.label.text = "Build directory: "

            row(androidNDKDirectory.label) {
                cell(androidNDKDirectory).align(AlignX.FILL)
            }
            androidNDKDirectory.label.text = "Android NDK directory: "

            row {
                cell(SeparatorComponent())
            }

            row {
                cell(configurationCommandText).applyToComponent {
                    isEditable = false
                    font = JBFont.label().asItalic()
                    foreground = UIUtil.getTextAreaForeground()
                    background = UIUtil.getFocusedBoundsColor()
                    border = JBUI.Borders.empty(8)
                }
                    .align(AlignY.BOTTOM)
                    .align(AlignX.FILL)
            }

            platformsModel.addListDataListener(object : ListDataListener {
                override fun contentsChanged(e: ListDataEvent) {
                    architecturesModel.removeAllElements()
                    for (architecture in XMakeConfiguration.getArchitecturesByPlatform(platformsModel.selectedItem.toString())) {
                        architecturesModel.addElement(architecture)
                    }
                    configurationCommandText.text = previewConfigurationCommand
                }

                override fun intervalAdded(e: ListDataEvent) {
                }

                override fun intervalRemoved(e: ListDataEvent) {
                }
            })

            architecturesModel.addListDataListener(object : ListDataListener {
                override fun contentsChanged(e: ListDataEvent) {
                    configurationCommandText.text = previewConfigurationCommand
                }

                override fun intervalAdded(e: ListDataEvent) {
                }

                override fun intervalRemoved(e: ListDataEvent) {
                }
            })

            modesModel.addListDataListener(object : ListDataListener {
                override fun contentsChanged(e: ListDataEvent) {
                    configurationCommandText.text = previewConfigurationCommand
                }

                override fun intervalAdded(e: ListDataEvent) {
                }

                override fun intervalRemoved(e: ListDataEvent) {
                }
            })

            additionalConfiguration.addKeyListener(object : KeyListener {
                override fun keyPressed(keyEvent: KeyEvent) {
                    configurationCommandText.text = previewConfigurationCommand
                }

                override fun keyReleased(keyEvent: KeyEvent) {
                    configurationCommandText.text = previewConfigurationCommand
                }

                override fun keyTyped(keyEvent: KeyEvent) {
                    configurationCommandText.text = previewConfigurationCommand
                }
            })

            verboseOutput.addItemListener(object : ItemListener {
                override fun itemStateChanged(e: ItemEvent) {
                    configurationCommandText.text = previewConfigurationCommand
                }
            })
        }
    }

    override fun disposeUIResources() {
    }

    override fun reset() {
        val xmakeConfiguration = project.xmakeConfigurationOrNull
        if (xmakeConfiguration != null) {
            // reset platforms
            platformsModel.removeAllElements()
            for (platform in xmakeConfiguration.platforms) {
                platformsModel.addElement(platform)
            }
            platformsModel.selectedItem = xmakeConfiguration.data.currentPlatform

            // reset architectures
            architecturesModel.removeAllElements()
            for (architecture in xmakeConfiguration.architectures) {
                architecturesModel.addElement(architecture)
            }
            architecturesModel.selectedItem = xmakeConfiguration.data.currentArchitecture

            // reset modes
            modesModel.removeAllElements()
            for (mode in xmakeConfiguration.modes) {
                modesModel.addElement(mode)
            }
            modesModel.selectedItem = xmakeConfiguration.data.currentMode

            // reset additional configuration
            additionalConfiguration.text = xmakeConfiguration.data.additionalConfiguration

            // reset build output directory
            buildOutputDirectory.component.text = xmakeConfiguration.data.buildOutputDirectory

            // reset android ndk directory
            androidNDKDirectory.component.text = xmakeConfiguration.data.androidNDKDirectory

            // reset verbose output
            verboseOutput.isSelected = xmakeConfiguration.data.verboseOutput

            // reset configuration command text
            configurationCommandText.text = previewConfigurationCommand
        }
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val xmakeConfiguration = project.xmakeConfigurationOrNull
        if (xmakeConfiguration != null) {
            xmakeConfiguration.data.currentPlatform = platformsModel.selectedItem.toString()
            xmakeConfiguration.data.currentArchitecture = architecturesModel.selectedItem.toString()
            xmakeConfiguration.data.currentMode = modesModel.selectedItem.toString()
            xmakeConfiguration.data.additionalConfiguration = additionalConfiguration.text
            xmakeConfiguration.data.buildOutputDirectory = buildOutputDirectory.component.text
            xmakeConfiguration.data.androidNDKDirectory = androidNDKDirectory.component.text
            xmakeConfiguration.data.verboseOutput = verboseOutput.isSelected
        }
    }

    override fun isModified(): Boolean {
        val xmakeConfiguration = project.xmakeConfigurationOrNull
        if (xmakeConfiguration != null) {
            if (xmakeConfiguration.data.currentPlatform != platformsModel.selectedItem.toString() ||
                xmakeConfiguration.data.currentArchitecture != architecturesModel.selectedItem.toString() ||
                xmakeConfiguration.data.currentMode != modesModel.selectedItem.toString() ||
                xmakeConfiguration.data.additionalConfiguration != additionalConfiguration.text ||
                xmakeConfiguration.data.buildOutputDirectory != buildOutputDirectory.component.text ||
                xmakeConfiguration.data.androidNDKDirectory != androidNDKDirectory.component.text ||
                xmakeConfiguration.data.verboseOutput != verboseOutput.isSelected
            ) {
                xmakeConfiguration.changed = true
                return true
            }
        }
        return false
    }

    override fun getDisplayName(): String = "XMake"
    override fun getHelpTopic(): String? = null

    private fun JPanel.makeWide() {
        preferredSize = Dimension(1000, height)
    }

    private val previewConfigurationCommand: String
        get() {
            var cmd = "xmake f"
            val platformItem = platformsModel.selectedItem
            if (platformItem != null) {
                cmd += " -p ${platformItem.toString()}"
            }
            if (architecturesModel.selectedItem != null) {
                cmd += " -a ${architecturesModel.selectedItem?.toString()}"
            }
            if (modesModel.selectedItem != null) {
                cmd += " -m ${modesModel.selectedItem?.toString()}"
            }
            if (platformItem?.toString() == "android" && androidNDKDirectory.component.text != "") {
                cmd += " --ndk=\"${androidNDKDirectory.component.text}\""
            }
            if (buildOutputDirectory.component.text != "") {
                cmd += " -o \"${buildOutputDirectory.component.text}\""
            }
            if (verboseOutput.isSelected) {
                cmd += " -v"
            }
            cmd += " ${additionalConfiguration.text}"
            return cmd
        }

    companion object {
        private val Log = Logger.getInstance(XMakeProjectConfigurable::class.java.getName())
    }
}*/