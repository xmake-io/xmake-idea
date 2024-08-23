package io.xmake.run

import com.intellij.execution.configuration.EnvironmentVariablesTextFieldWithBrowseButton
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.CheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ComboBoxPredicate
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.target.TargetManager
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.project.toolkit.ui.ToolkitComboBox
import io.xmake.project.toolkit.ui.ToolkitListItem
import io.xmake.shared.xmakeConfiguration
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

    private var toolkit: Toolkit? = runConfiguration.runToolkit
    private val toolkitComboBox = ToolkitComboBox(::toolkit)

    // the targets ui
    private val targetsModel = DefaultComboBoxModel<String>()
    private val targetsComboBox = ComboBox(targetsModel).apply { item = runConfiguration.runTarget }

    private val platformsModel = DefaultComboBoxModel(project.xmakeConfiguration.platforms)
    private val platformsComboBox = ComboBox(platformsModel).apply { item = runConfiguration.runPlatform }

    private val architecturesModel = DefaultComboBoxModel(project.xmakeConfiguration.architectures)
    private val architecturesComboBox = ComboBox(architecturesModel).apply { item = runConfiguration.runArchitecture }

    private val modesModel = DefaultComboBoxModel(project.xmakeConfiguration.modes)
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

    // reset editor from configuration
    override fun resetEditorFrom(configuration: XMakeRunConfiguration) {

        toolkit = configuration.runToolkit

        // reset targets
        targetsModel.removeAllElements()
        targetsModel.selectedItem = configuration.runTarget

        platformsComboBox.item = configuration.runPlatform

        architecturesComboBox.item = configuration.runArchitecture

        modesComboBox.item = configuration.runMode

        // reset run arguments
        runArguments.text = configuration.runArguments

        // reset environment variables
        environmentVariables.data = configuration.runEnvironment

        workingDirectoryBrowser.text = configuration.runWorkingDir

        buildDirectoryBrowser.text = configuration.buildDirectory

        androidNDKDirectoryBrowser.text = configuration.androidNDKDirectory

        enableVerbose = configuration.enableVerbose

        additionalConfiguration.text = configuration.additionalConfiguration
    }

    // apply editor to configuration
    override fun applyEditorTo(configuration: XMakeRunConfiguration) {

        configuration.runToolkit = toolkit

        configuration.runTarget = (targetsModel.selectedItem ?: "").toString()

        configuration.runPlatform = platformsComboBox.item

        configuration.runArchitecture = architecturesComboBox.item

        configuration.runMode = modesComboBox.item

        configuration.runArguments = runArguments.text

        configuration.runEnvironment = environmentVariables.data

        configuration.runWorkingDir = workingDirectoryBrowser.text

        configuration.buildDirectory = buildDirectoryBrowser.text

        configuration.androidNDKDirectory = androidNDKDirectoryBrowser.text

        configuration.enableVerbose = enableVerbose

        configuration.additionalConfiguration = additionalConfiguration.text

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
                    }
                }
                activatedToolkit?.let {
                    workingDirectoryBrowser.addBrowserListenerByToolkit(it)
                    buildDirectoryBrowser.addBrowserListenerByToolkit(it)
                    androidNDKDirectoryBrowser.addBrowserListenerByToolkit(it)
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
                            architecturesModel.removeAllElements()
                            architecturesModel.addAll(project.xmakeConfiguration.architectures.toMutableList())
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
                    label("Mode:")
                }
                row {
                    cell(modesComboBox).align(AlignX.FILL)
                }
            }.resizableColumn()
        }.layout(RowLayout.PARENT_GRID)

        separator()

        row("Target:") {
            cell(targetsComboBox).applyToComponent {
                addPopupMenuListener(object : PopupMenuListenerAdapter() {
                    override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
                        super.popupMenuWillBecomeVisible(e)
                        targetsModel.removeAllElements()
                        with(runConfiguration) {
                            if (runToolkit != null && runWorkingDir.isNotEmpty()) {
                                TargetManager.getInstance(project)
                                    .detectXMakeTarget(runToolkit!!, runConfiguration.runWorkingDir).forEach { target ->
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
//        environmentVariables.label
        row("Environment variables") {
            cell(environmentVariables).align(AlignX.FILL)
        }

        row("Working directory") {
            cell(workingDirectoryBrowser).align(AlignX.FILL)
        }

        collapsibleGroup("Additional Configurations") {
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
                        when (toolkit.host.type) {
                            LOCAL -> {}
                            WSL -> {
                                val wslDistribution = toolkit.host.target as? WSLDistribution
                                syncProjectByWslSync(
                                    scope,
                                    project,
                                    wslDistribution!!,
                                    workingDirectoryPath,
                                    SyncDirection.LOCAL_TO_UPSTREAM
                                )
                            }

                            SSH -> {
                                val sshConfig = toolkit.host.target as? SshConfig
                                syncProjectBySftp(
                                    scope,
                                    project,
                                    sshConfig!!,
                                    workingDirectoryPath,
                                    SyncDirection.LOCAL_TO_UPSTREAM
                                )
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
