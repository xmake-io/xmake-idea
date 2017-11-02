package org.tboox.xmake.project

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.Label
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.panel
import org.tboox.xmake.shared.XMakeConfiguration
import org.tboox.xmake.shared.xmakeConfiguration
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.JTextArea
import javax.swing.DefaultComboBoxModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener

class XMakeProjectConfigurable(
        private val project: Project
) : Configurable, Configurable.NoScroll {

    // the xmake configuration
    val xmakeConfiguration = project.xmakeConfiguration

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

    // the configuration command text
    private val configurationCommandText = JTextArea(10, 10)

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

    // verbose output
    private val verboseOutput = CheckBox("Show verbose output", true)

    override fun createComponent(): JComponent = panel {

        labeledRow("Platform:", platformsComboBox) {
            platformsComboBox()
        }

        labeledRow("Architecture:", architecturesComboBox) {
            architecturesComboBox()
        }

        labeledRow("Mode:", modesComboBox) {
            modesComboBox()
        }

        labeledRow("Additional configuration:", additionalConfiguration) {
            additionalConfiguration.apply {
                dialogCaption = "Additional configuration"
                makeWide()
            }()
        }

        row { verboseOutput() }

        row(workingDirectory.label) {
            workingDirectory.apply { makeWide() }()
        }

        row {
            configurationCommandText()
        }
        configurationCommandText.setEditable(false)

        platformsModels.addListDataListener(object: ListDataListener {
            override fun contentsChanged(e: ListDataEvent) {
                architecturesModels.removeAllElements()
                for (architecture in XMakeConfiguration.getArchitecturesByPlatform(platformsModels.selectedItem.toString())) {
                    architecturesModels.addElement(architecture)
                }
                configurationCommandText.text = previewConfigurationCommand
            }
            override fun intervalAdded(e: ListDataEvent) {
            }
            override fun intervalRemoved(e: ListDataEvent) {
            }
        })

        architecturesModels.addListDataListener(object: ListDataListener {
            override fun contentsChanged(e: ListDataEvent) {
                configurationCommandText.text = previewConfigurationCommand
            }
            override fun intervalAdded(e: ListDataEvent) {
            }
            override fun intervalRemoved(e: ListDataEvent) {
            }
        })

        modesModels.addListDataListener(object: ListDataListener {
            override fun contentsChanged(e: ListDataEvent) {
                configurationCommandText.text = previewConfigurationCommand
            }
            override fun intervalAdded(e: ListDataEvent) {
            }
            override fun intervalRemoved(e: ListDataEvent) {
            }
        })

        additionalConfiguration.textField.addKeyListener(object: KeyListener {
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

        verboseOutput.addItemListener(object: ItemListener {
            override fun itemStateChanged(e: ItemEvent) {
                configurationCommandText.text = previewConfigurationCommand
            }
        })
    }

    override fun disposeUIResources() {
    }

    override fun reset() {

        // reset platforms
        platformsModels.removeAllElements()
        for (platform in xmakeConfiguration.platforms) {
            platformsModels.addElement(platform)
        }
        platformsModels.selectedItem = xmakeConfiguration.data.currentPlatform

        // reset architectures
        architecturesModels.removeAllElements()
        for (architecture in xmakeConfiguration.architectures) {
            architecturesModels.addElement(architecture)
        }
        architecturesModels.selectedItem = xmakeConfiguration.data.currentArchitecture

        // reset modes
        modesModels.removeAllElements()
        for (mode in xmakeConfiguration.modes) {
            modesModels.addElement(mode)
        }
        modesModels.selectedItem = xmakeConfiguration.data.currentMode

        // reset additional configuration
        additionalConfiguration.text = xmakeConfiguration.data.additionalConfiguration

        // reset working directory
        workingDirectory.component.text = xmakeConfiguration.data.workingDirectory

        // reset verbose output
        verboseOutput.isSelected = xmakeConfiguration.data.verboseOutput

        // reset configuration command text
        configurationCommandText.text = previewConfigurationCommand
    }

    @Throws(ConfigurationException::class)
    override fun apply() {

        xmakeConfiguration.data.currentPlatform         = platformsModels.selectedItem.toString()
        xmakeConfiguration.data.currentArchitecture     = architecturesModels.selectedItem.toString()
        xmakeConfiguration.data.currentMode             = modesModels.selectedItem.toString()
        xmakeConfiguration.data.additionalConfiguration = additionalConfiguration.text
        xmakeConfiguration.data.workingDirectory        = workingDirectory.component.text
        xmakeConfiguration.data.verboseOutput           = verboseOutput.isSelected
    }

    override fun isModified(): Boolean {

        if (xmakeConfiguration.data.currentPlatform != platformsModels.selectedItem.toString())
            return true
        if (xmakeConfiguration.data.currentArchitecture != architecturesModels.selectedItem.toString())
            return true
        if (xmakeConfiguration.data.currentMode != modesModels.selectedItem.toString())
            return true
        if (xmakeConfiguration.data.additionalConfiguration != additionalConfiguration.text)
            return true
        if (xmakeConfiguration.data.workingDirectory != workingDirectory.component.text)
            return true
        if (xmakeConfiguration.data.verboseOutput != verboseOutput.isSelected)
            return true
        return false
    }

    override fun getDisplayName(): String = "XMake"
    override fun getHelpTopic(): String? = null

    private fun JPanel.makeWide() {
        preferredSize = Dimension(1000, height)
    }

    private fun LayoutBuilder.labeledRow(labelText: String, component: JComponent, init: Row.() -> Unit) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }

    private val previewConfigurationCommand: String
        get() {
            var cmd = "xmake f"
            if (platformsModels.selectedItem != null) {
                cmd += " -p ${platformsModels.selectedItem?.toString()}"
            }
            if (architecturesModels.selectedItem != null) {
                cmd += " -a ${architecturesModels.selectedItem?.toString()}"
            }
            if (modesModels.selectedItem != null) {
                cmd += " -m ${modesModels.selectedItem?.toString()}"
            }
            if (verboseOutput.isSelected) {
                cmd += " -v"
            }
            cmd += " ${additionalConfiguration.text}"
            return cmd
        }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeProjectConfigurable::class.java.getName())
    }
}