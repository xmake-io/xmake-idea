package io.xmake.project

import com.intellij.execution.ExecutionBundle
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
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.panel
import io.xmake.shared.XMakeConfiguration
import io.xmake.shared.xmakeConfiguration
import io.xmake.shared.xmakeConfigurationOrNull
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.DefaultComboBoxModel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.io.IOException

class XMakeProjectConfigurable(
        project: Project
) : Configurable, Configurable.NoScroll {

    // the platforms ui
    private val project = project
    private val platformsModel = DefaultComboBoxModel<String>()
    private val platformsComboBox = ComboBox<String>(platformsModel)

    // the architectures ui
    private val architecturesModel = DefaultComboBoxModel<String>()
    private val architecturesComboBox = ComboBox<String>(architecturesModel)

    // the modes ui
    private val modesModel = DefaultComboBoxModel<String>()
    private val modesComboBox = ComboBox<String>(modesModel)

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
                makeWide()
            }()
        }

        row { verboseOutput() }

        row(workingDirectory.label) {
            workingDirectory.apply { makeWide() }()
        }

        row(buildOutputDirectory.label) {
            buildOutputDirectory.apply { makeWide() }()
        }
        buildOutputDirectory.label.text = "Build directory: "

        row(androidNDKDirectory.label) {
            androidNDKDirectory.apply { makeWide() }()
        }
        androidNDKDirectory.label.text = "Android NDK directory: "

        row {
            configurationCommandText()
        }
        configurationCommandText.setEditable(false)

        platformsModel.addListDataListener(object: ListDataListener {
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

        architecturesModel.addListDataListener(object: ListDataListener {
            override fun contentsChanged(e: ListDataEvent) {
                configurationCommandText.text = previewConfigurationCommand
            }
            override fun intervalAdded(e: ListDataEvent) {
            }
            override fun intervalRemoved(e: ListDataEvent) {
            }
        })

        modesModel.addListDataListener(object: ListDataListener {
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

            // reset working directory
            workingDirectory.component.text = xmakeConfiguration.data.workingDirectory

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
            xmakeConfiguration.data.workingDirectory = workingDirectory.component.text
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
                xmakeConfiguration.data.workingDirectory != workingDirectory.component.text ||
                xmakeConfiguration.data.buildOutputDirectory != buildOutputDirectory.component.text ||
                xmakeConfiguration.data.androidNDKDirectory != androidNDKDirectory.component.text ||
                xmakeConfiguration.data.verboseOutput != verboseOutput.isSelected) {
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

    private fun LayoutBuilder.labeledRow(labelText: String, component: JComponent, init: Row.() -> Unit) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }

    private val previewConfigurationCommand: String
        get() {
            var cmd = "xmake f"
            var platformItem = platformsModel.selectedItem
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
}