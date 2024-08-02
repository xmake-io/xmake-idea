package io.xmake.run

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.xmake.project.target.TargetManager
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.project.toolkit.ui.ToolkitComboBox
import io.xmake.project.toolkit.ui.ToolkitListItem
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.PopupMenuEvent

class XMakeRunConfigurationEditor(
    private val project: Project,
    private val runConfiguration: XMakeRunConfiguration,
) : SettingsEditor<XMakeRunConfiguration>() {


    private val toolkitComboBox = ToolkitComboBox(runConfiguration::runToolkit)

    // the targets ui
    private val targetsModel = DefaultComboBoxModel<String>()
    private val targetsComboBox = ComboBox(targetsModel)

    // the run arguments
    private val runArguments = RawCommandLineEditor()

    // the environment variables
    private val environmentVariables = EnvironmentVariablesComponent()

    private val browser = TextFieldWithBrowseButton()

    // reset editor from configuration
    override fun resetEditorFrom(configuration: XMakeRunConfiguration) {

        // reset targets
        targetsModel.removeAllElements()
        targetsModel.selectedItem = configuration.runTarget

        // reset run arguments
        runArguments.text = configuration.runArguments

        // reset environment variables
        environmentVariables.envData = configuration.runEnvironment

        browser.text = configuration.runWorkingDir
    }

    // apply editor to configuration
    override fun applyEditorTo(configuration: XMakeRunConfiguration) {

        configuration.runTarget = (targetsModel.selectedItem ?: "").toString()
        configuration.runArguments = runArguments.text
        configuration.runEnvironment = environmentVariables.envData
        configuration.runWorkingDir = browser.text
    }

    // create editor
    override fun createEditor(): JComponent = panel {

        row("Xmake Toolkit:") {
            cell(toolkitComboBox).align(AlignX.FILL)
        }

        row("Target:") {
            cell(targetsComboBox).applyToComponent {
                addPopupMenuListener(object : PopupMenuListenerAdapter() {
                    override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                        super.popupMenuWillBecomeVisible(e)
                        targetsModel.removeAllElements()
                        with(runConfiguration){
                            if (runToolkit != null && runWorkingDir.isNotEmpty()){
                                TargetManager.getInstance(project)
                                    .detectXmakeTarget(runToolkit!!, runConfiguration.runWorkingDir).forEach { target ->
                                        targetsModel.addElement(target)
                                    }
                            }
                        }
                    }
                })
            }.align(AlignX.FILL)
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
