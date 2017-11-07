package org.tboox.xmake.run

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
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
import org.tboox.xmake.shared.xmakeConfiguration
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.DefaultComboBoxModel

class XMakeRunConfigurationEditor(private val project: Project) : SettingsEditor<XMakeRunConfiguration>() {

    // the xmake configuration
    val xmakeConfiguration = project.xmakeConfiguration

    // the targets ui
    private val targetsModel = DefaultComboBoxModel<String>()
    private val targetsComboBox = ComboBox<String>(targetsModel)

    // the run arguments
    private val runArguments = RawCommandLineEditor()

    // the environment variables
    private val environmentVariables = EnvironmentVariablesComponent()

    // reset editor from configuration
    override fun resetEditorFrom(configuration: XMakeRunConfiguration) {

        // reset targets
        targetsModel.removeAllElements()
        for (target in xmakeConfiguration.targets) {
            targetsModel.addElement(target)
        }
        targetsModel.selectedItem = configuration.runTarget

        // reset run arguments
        runArguments.text = configuration.runArguments

        // reset environment variables
        environmentVariables.envData = configuration.runEnvironment
    }

    // apply editor to configuration
    override fun applyEditorTo(configuration: XMakeRunConfiguration) {

        configuration.runTarget         = targetsModel.selectedItem.toString()
        configuration.runArguments      = runArguments.text
        configuration.runEnvironment    = environmentVariables.envData
    }

    // create editor
    override fun createEditor(): JComponent = panel {

        labeledRow("Default target:", targetsComboBox) {
            targetsComboBox(CCFlags.push)
        }

        labeledRow("Run arguments:", runArguments) {
            runArguments.apply {
                dialogCaption = "Run arguments"
                makeWide()
            }()
        }
        row(environmentVariables.label) { environmentVariables.apply { makeWide() }() }
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
