package io.xmake.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.DialogPanel
import com.intellij.util.messages.MessageBusConnection
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import io.xmake.project.directory.ui.XMakeProjectDirectoryPanel
import io.xmake.project.toolkit.ToolkitListener
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.project.toolkit.ui.ToolkitListItem
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList

class XMakeSettingsConfigurable(private val project: Project) : SearchableConfigurable {
    private val settings = XMakeSettings.getInstance(project)
    private var projectDirectoryPanel: XMakeProjectDirectoryPanel? = null
    private val toolkitManager = ToolkitManager.getInstance()
    private var messageBusConnection: MessageBusConnection? = null
    private val toolkitListModel = DefaultListModel<ToolkitListItem>()
    private var toolkitList: JBList<ToolkitListItem>? = null
    private var myPanel: DialogPanel? = null

    override fun createComponent(): JComponent {
        val toolkitList = JBList(toolkitListModel).apply {
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
                    val text = value?.text.orEmpty()
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
        this.toolkitList = toolkitList
        refreshToolkitList()

        val projectDirectoryPanel = XMakeProjectDirectoryPanel(project)
        this.projectDirectoryPanel = projectDirectoryPanel

        messageBusConnection = project.messageBus.connect().apply {
            subscribe(
                ToolkitListener.TOPIC,
                object : ToolkitListener {
                    override fun toolkitsChanged() {
                        ApplicationManager.getApplication().invokeLater {
                            refreshToolkitList()
                            projectDirectoryPanel.refreshToolkits()
                        }
                    }
                },
            )
        }

        val decorator = ToolbarDecorator.createDecorator(toolkitList)
        decorator.setRemoveAction {
            val toolkit = (toolkitList.selectedValue as? ToolkitListItem.Entry)?.toolkit
                ?: return@setRemoveAction
            if (Messages.showYesNoDialog(
                    project,
                    "Unregister ${toolkit.name}?",
                    "Unregister Toolkit",
                    Messages.getQuestionIcon()
                ) == Messages.YES
            ) {
                toolkitManager.unregister(toolkit.id)
                toolkitListModel.removeElement(toolkitList.selectedValue)
            }
        }

        val panel = panel {
            groupRowsRange("Project Directory") {
                projectDirectoryPanel.attachTo(this)
            }

            groupRowsRange("IntelliSense") {
                row("Compile commands path:") {
                    textField()
                        .bindText(settings.state::compileCommandsPath)
                        .align(AlignX.FILL)
                        .comment("Relative to the project directory. Defaults to compile_commands.json.")
                }
                row {
                    checkBox("Auto-update compile_commands.json after build")
                        .bindSelected(settings.state::autoUpdateCompileCommands)
                }
            }

            groupRowsRange("Toolkit", indent = false) {
                row {
                    cell(decorator.createPanel()).align(Align.FILL)
                }.resizableRow()
            }
        }
        myPanel = panel
        return panel
    }

    override fun getPreferredFocusedComponent(): JComponent? =
        projectDirectoryPanel?.focusableComponent ?: myPanel

    private fun refreshToolkitList() {
        val selectedId = (toolkitList?.selectedValue as? ToolkitListItem.Entry)?.id
        toolkitListModel.clear()
        toolkitManager.registeredToolkits(project).forEach { toolkit ->
            toolkitListModel.addElement(ToolkitListItem.Entry(toolkit))
        }
        if (selectedId != null) {
            val selectedIndex = (0 until toolkitListModel.size)
                .firstOrNull { index -> toolkitListModel.getElementAt(index).id == selectedId }
            if (selectedIndex != null) toolkitList?.selectedIndex = selectedIndex
        }
    }

    override fun disposeUIResources() {
        messageBusConnection?.disconnect()
        messageBusConnection = null
        toolkitList = null
        myPanel = null
        projectDirectoryPanel = null
    }

    override fun isModified(): Boolean {
        return projectDirectoryPanel?.isModified == true || myPanel?.isModified() == true
    }

    override fun apply() {
        projectDirectoryPanel?.apply()
        myPanel?.apply()
    }

    override fun reset() {
        projectDirectoryPanel?.reset()
        myPanel?.reset()
    }

    override fun getDisplayName() = "XMake"

    override fun getId(): String = "XMakeSettings"
}
