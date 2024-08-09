package io.xmake.run

import com.intellij.execution.configuration.EnvironmentVariablesComponent
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComboBoxPredicate
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.target.TargetManager
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.project.toolkit.ui.ToolkitComboBox
import io.xmake.project.toolkit.ui.ToolkitListItem
import io.xmake.utils.execute.SyncDirection
import io.xmake.utils.execute.syncProjectBySftp
import io.xmake.utils.execute.syncProjectByWslSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Dimension
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.PopupMenuEvent

class XMakeRunConfigurationEditor(
    private val project: Project,
    private val runConfiguration: XMakeRunConfiguration,
) : SettingsEditor<XMakeRunConfiguration>() {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val toolkitComboBox = ToolkitComboBox(runConfiguration::runToolkit)

    // the targets ui
    private val targetsModel = DefaultComboBoxModel<String>()
    private val targetsComboBox = ComboBox(targetsModel)

    // the run arguments
    private val runArguments = RawCommandLineEditor()

    // the environment variables
    private val environmentVariables = EnvironmentVariablesComponent()

    private val browser = DirectoryBrowser(project)

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
            cell(toolkitComboBox).align(AlignX.FILL).applyToComponent {
                // Todo: Store previously selected toolkit to restore it if not applied.
                whenItemSelected { item ->
                    browser.removeBrowserAllListener()
                    (item as? ToolkitListItem.ToolkitItem)?.toolkit?.let {
                        browser.addBrowserListenerByToolkit(it)
                    }
                }
                activatedToolkit?.let { browser.addBrowserListenerByToolkit(it) }
            }
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
            cell(browser).align(AlignX.FILL)
        }

        row("Sync Directory:") {
            button("Upload") {
                toolkitComboBox.activatedToolkit?.let { toolkit ->
                    val workingDirectoryPath = browser.text

                    scope.launch(Dispatchers.IO) {
                        when (toolkit.host.type) {
                            LOCAL -> {}
                            WSL -> {
                                val wslDistribution = toolkit.host.target as? WSLDistribution
                                syncProjectByWslSync(scope, project, wslDistribution!!, workingDirectoryPath, SyncDirection.LOCAL_TO_UPSTREAM)
                            }

                            SSH -> {
                                val sshConfig = toolkit.host.target as? SshConfig
                                syncProjectBySftp(scope, project, sshConfig!!, workingDirectoryPath, SyncDirection.LOCAL_TO_UPSTREAM)
                            }
                        }
                    }
                }
            }
        }.visibleIf(ComboBoxPredicate<ToolkitListItem>(toolkitComboBox) {
            val toolkit = (it as? ToolkitListItem.ToolkitItem)?.toolkit
            toolkit?.isOnRemote ?: false
        })
    }

    private fun JPanel.makeWide() {
        preferredSize = Dimension(1000, height)
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunConfigurationEditor::class.java.getName())
    }
}
