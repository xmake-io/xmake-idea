package io.xmake.run

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.DefaultComboBoxModel

class XMakeRunConfigurationEditor(
    private val project: Project,
    private val runConfiguration: XMakeRunConfiguration,
) : SettingsEditor<XMakeRunConfiguration>() {



    // the targets ui
    private val targetsModel = DefaultComboBoxModel<String>()
    private val targetsComboBox = ComboBox<String>(targetsModel)

    // the run arguments
    private val runArguments = RawCommandLineEditor()

    // the environment variables
    private val environmentVariables = EnvironmentVariablesComponent()

    private val browser = TextFieldWithBrowseButton()

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

        browser.text = configuration.runWorkingDir
    }

    // apply editor to configuration
    override fun applyEditorTo(configuration: XMakeRunConfiguration) {

        configuration.runTarget         = targetsModel.selectedItem.toString()
        configuration.runArguments      = runArguments.text
        configuration.runEnvironment    = environmentVariables.envData
        configuration.runWorkingDir = browser.text
    }

    // create editor
    override fun createEditor(): JComponent = panel {
        row("Default target:") {
            cell(targetsComboBox).align(AlignX.FILL)
        }

        row("Program arguments:") {
            cell(runArguments).align(AlignX.FILL)
        }
        row(environmentVariables.label) {
            cell(environmentVariables).align(AlignX.FILL)
        }
        row("Working directory") {
            cell(browser).applyToComponent {
            }.align(AlignX.FILL)
        }
    }

    private fun JPanel.makeWide() {
        preferredSize = Dimension(1000, height)
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunConfigurationEditor::class.java.getName())
    }
}
