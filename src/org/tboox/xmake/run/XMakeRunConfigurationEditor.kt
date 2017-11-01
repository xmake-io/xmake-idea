package org.tboox.xmake.run

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.CheckBox
import com.intellij.ui.layout.*
import com.intellij.ui.components.Label
import org.tboox.xmake.shared.XMakeConfiguration
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.DefaultComboBoxModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

class XMakeRunConfigurationEditor(private val project: Project) : SettingsEditor<XMakeRunConfiguration>() {

    // the targets ui
    private val targetsModels = DefaultComboBoxModel<String>()
    private val targetsComboBox = ComboBox<String>(targetsModels)

    // the platforms ui
    private val platformsModels = DefaultComboBoxModel<String>()
    private val platformsComboBox = ComboBox<String>(platformsModels)

    // the architectures ui
    private val architecturesModels = DefaultComboBoxModel<String>()
    private val architecturesComboBox = ComboBox<String>(architecturesModels)

    // the modes ui
    private val modesModels = DefaultComboBoxModel<String>()
    private val modesComboBox = ComboBox<String>(modesModels)

    // the additional configuration
    private val additionalConfiguration = RawCommandLineEditor()

    // the working directory
    private val workingDirectory = run {
        val textField = TextFieldWithBrowseButton().apply {
            val fileChooser = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                title = ExecutionBundle.message("select.working.directory.message")
            }
            addBrowseFolderListener(null, null, null, fileChooser)
        }
        LabeledComponent.create(textField, ExecutionBundle.message("run.configuration.working.directory.label"))
    }

    // the environment variables
    private val environmentVariables = EnvironmentVariablesComponent()

    // verbose output
    private val verboseOutput = CheckBox("Show verbose output", true)

    // reset editor from configuration
    override fun resetEditorFrom(configuration: XMakeRunConfiguration) {

        // reset targets
        targetsModels.removeAllElements()
        for (target in XMakeConfiguration.targets) {
            targetsModels.addElement(target)
        }
        targetsModels.selectedItem = XMakeConfiguration.currentTarget

        // reset platforms
        platformsModels.removeAllElements()
        for (platform in XMakeConfiguration.platforms) {
            platformsModels.addElement(platform)
        }
        platformsModels.selectedItem = XMakeConfiguration.currentPlatfrom

        // reset architectures
        architecturesModels.removeAllElements()
        for (architecture in XMakeConfiguration.architectures) {
            architecturesModels.addElement(architecture)
        }
        architecturesModels.selectedItem = XMakeConfiguration.currentArchitecture

        // reset modes
        modesModels.removeAllElements()
        for (mode in XMakeConfiguration.modes) {
            modesModels.addElement(mode)
        }
        modesModels.selectedItem = XMakeConfiguration.currentMode

        // reset additional configuration
        additionalConfiguration.text = XMakeConfiguration.additionalConfiguration

        // reset working directory
        workingDirectory.component.text = XMakeConfiguration.workingDirectory

        // reset environment variables
        environmentVariables.envData = XMakeConfiguration.environmentVariables

        // reset verbose output
        verboseOutput.isSelected = XMakeConfiguration.verboseOutput
    }

    // apply editor to configuration
    override fun applyEditorTo(configuration: XMakeRunConfiguration) {

        XMakeConfiguration.currentTarget             = targetsModels.selectedItem.toString()
        XMakeConfiguration.currentPlatfrom           = platformsModels.selectedItem.toString()
        XMakeConfiguration.currentArchitecture       = architecturesModels.selectedItem.toString()
        XMakeConfiguration.currentMode               = modesModels.selectedItem.toString()
        XMakeConfiguration.additionalConfiguration   = additionalConfiguration.text
        XMakeConfiguration.workingDirectory          = workingDirectory.component.text
        XMakeConfiguration.environmentVariables      = environmentVariables.envData
        XMakeConfiguration.verboseOutput             = verboseOutput.isSelected
    }

    // create editor
    override fun createEditor(): JComponent = panel {

        labeledRow("Default target:", targetsComboBox) {
            targetsComboBox(CCFlags.push)
        }

        labeledRow("Platform:", platformsComboBox) {
            platformsComboBox(CCFlags.push)
        }

        labeledRow("Architecture:", architecturesComboBox) {
            architecturesComboBox(CCFlags.push)
        }

        labeledRow("Mode:", modesComboBox) {
            modesComboBox(CCFlags.push)
        }

        labeledRow("Additional configuration:", additionalConfiguration) {
            additionalConfiguration.apply {
                dialogCaption = "Additional configuration"
                makeWide()
            }()
        }

        row { verboseOutput() }

        row(environmentVariables.label) { environmentVariables.apply { makeWide() }() }

        row(workingDirectory.label) {
            workingDirectory.apply { makeWide() }()
        }

        platformsModels.addListDataListener(object: ListDataListener {
            override fun contentsChanged(e: ListDataEvent) {
                architecturesModels.removeAllElements()
                for (architecture in XMakeConfiguration.getArchitecturesByPlatform(platformsModels.selectedItem.toString())) {
                    architecturesModels.addElement(architecture)
                }
            }
            override fun intervalAdded(e: ListDataEvent) {
            }
            override fun intervalRemoved(e: ListDataEvent) {
            }
        })
    }

    private fun JPanel.makeWide() {
        preferredSize = Dimension(1000, height)
    }

    private fun LayoutBuilder.labeledRow(labelText: String, component: JComponent, init: Row.() -> Unit) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunConfigurationEditor::class.java.getName())
    }
}
