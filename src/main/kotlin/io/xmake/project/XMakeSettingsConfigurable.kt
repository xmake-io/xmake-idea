package io.xmake.project

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.project.toolkit.ui.ToolkitListItem
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList

class XMakeSettingsConfigurable(private val project: Project) : Configurable {
    private val settings = XMakeSettings.getInstance(project)
    private val toolkitManager = ToolkitManager.getInstance()
    private var myPanel: com.intellij.openapi.ui.DialogPanel? = null
    private var profilesPanel: XMakeProfilesPanel? = null

    override fun createComponent(): JComponent {
        val profilesPanel = XMakeProfilesPanel(project).also { profilesPanel = it }
        val registeredToolkit = toolkitManager.getRegisteredToolkits()
        val listModel = DefaultListModel<ToolkitListItem>().apply { registeredToolkit.forEach {
            addElement(ToolkitListItem.ToolkitItem(it).asRegistered()) }
        }
        val toolkitList = JBList(listModel).apply {

            cellRenderer = object : ColoredListCellRenderer<ToolkitListItem>() {
                override fun customizeCellRenderer(
                    list: JList<out ToolkitListItem>,
                    value: ToolkitListItem?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {

                    isOpaque = false
                    icon = value?.icon
                    val text = value?.let { value.text } ?: ""
                    val secondaryText = value?.secondaryText
                    val tertiaryText = value?.tertiaryText

                    append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    if (secondaryText != null)
                        append(" - $secondaryText ", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
                    if (tertiaryText != null)
                        append(" $tertiaryText", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }
        val decorator = ToolbarDecorator.createDecorator(toolkitList)

        decorator.setRemoveAction {
            val toolkit = (toolkitList.selectedValue as ToolkitListItem.ToolkitItem).toolkit
            if (Messages.showYesNoDialog(
                    "Unregister ${toolkit.name}?",
                    "Unregister Toolkit",
                    Messages.getQuestionIcon()
                ) == Messages.YES
            ) {
                toolkitManager.unregisterToolkit(toolkit)
                listModel.removeElement(toolkitList.selectedValue)
            }
        }

        val panel = panel {
            group("Profiles") {
                row {
                    cell(profilesPanel.component).align(Align.FILL)
                }
                row {
                    checkBox("Verbose output")
                        .bindSelected(settings.state::verbose)
                        .comment("Pass -v to xmake commands run from the IDE.")
                }
            }
            group("Intellisense") {
                row("Compile commands path:") {
                    textField()
                        .bindText(settings.state::compileCommandsPath)
                        .comment("Path to generate compile_commands.json (relative to project root). Default: ./compile_commands.json")
                }
                row {
                    checkBox("Auto-update compile_commands.json after build")
                        .bindSelected(settings.state::autoUpdateCompileCommands)
                }
                row {
                    checkBox("Auto-reload xmake configuration when profile/mode changes")
                        .bindSelected(settings.state::autoReloadConfigOnSwitch)
                        .comment("Runs 'xmake f ...' immediately after switching the profile or build mode from the toolbar.")
                }
            }
            group("Toolkit") {
                row {
                    cell(decorator.createPanel()).align(Align.FILL)
                }
            }
        }
        myPanel = panel
        return panel
    }

    override fun isModified(): Boolean {
        return (myPanel?.isModified() ?: false) || (profilesPanel?.isModified() ?: false)
    }

    override fun apply() {
        myPanel?.apply()
        profilesPanel?.apply()
    }

    override fun reset() {
        myPanel?.reset()
        profilesPanel?.reset()
    }

    override fun disposeUIResources() {
        profilesPanel?.let { Disposer.dispose(it) }
        profilesPanel = null
        myPanel = null
    }

    override fun getDisplayName() = "Xmake"
}
