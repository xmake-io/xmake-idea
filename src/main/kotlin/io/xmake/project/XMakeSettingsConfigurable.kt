package io.xmake.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
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

class XMakeSettingsConfigurable(private val project: Project) :
    BoundConfigurable("XMake"), SearchableConfigurable {

    private val settings = XMakeSettings.getInstance(project)
    private var projectDirectoryPanel: XMakeProjectDirectoryPanel? = null
    private val toolkitManager = ToolkitManager.getInstance()
    private val toolkitListModel = DefaultListModel<ToolkitListItem>()
    private var toolkitList: JBList<ToolkitListItem>? = null

    private val pendingToolkitRemovals = mutableSetOf<String>()

    override fun getId(): String = "XMakeSettings"

    override fun createPanel(): DialogPanel {
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

        project.messageBus.connect(disposable!!).subscribe(
            ToolkitListener.TOPIC,
            ToolkitListener {
                ApplicationManager.getApplication().invokeLater({
                    if (project.isDisposed) return@invokeLater
                    refreshToolkitList()
                    projectDirectoryPanel.refreshToolkits()
                }, ModalityState.any())
            },
        )

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
                pendingToolkitRemovals += toolkit.id
                toolkitListModel.removeElement(toolkitList.selectedValue)
            }
        }

        return panel {
            groupRowsRange("Project Directory") {
                projectDirectoryPanel.attachTo(this)
            }

            groupRowsRange("IntelliSense") {
                row("Compile commands directory:") {
                    textField()
                        .bindText(settings.state::compileCommandsPath)
                        .align(AlignX.FILL)
                        .comment("Directory for the generated compile_commands.json, relative to the project directory.")
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
    }

    override fun getPreferredFocusedComponent(): JComponent? =
        projectDirectoryPanel?.focusableComponent ?: super<BoundConfigurable>.getPreferredFocusedComponent()

    private fun refreshToolkitList() {
        val selectedId = (toolkitList?.selectedValue as? ToolkitListItem.Entry)?.id
        toolkitListModel.clear()
        toolkitManager.registeredToolkits(project)
            .filter { toolkit -> toolkit.id !in pendingToolkitRemovals }
            .forEach { toolkit ->
                toolkitListModel.addElement(ToolkitListItem.Entry(toolkit))
            }
        if (selectedId != null) {
            val selectedIndex = (0 until toolkitListModel.size)
                .firstOrNull { index -> toolkitListModel.getElementAt(index).id == selectedId }
            if (selectedIndex != null) toolkitList?.selectedIndex = selectedIndex
        }
    }

    override fun disposeUIResources() {
        toolkitList = null
        projectDirectoryPanel = null
        super<BoundConfigurable>.disposeUIResources()
    }

    override fun isModified(): Boolean =
        pendingToolkitRemovals.isNotEmpty() ||
                projectDirectoryPanel?.isModified == true ||
                super<BoundConfigurable>.isModified()

    override fun apply() {
        projectDirectoryPanel?.apply()
        super<BoundConfigurable>.apply()
        pendingToolkitRemovals.forEach(toolkitManager::unregister)
        pendingToolkitRemovals.clear()
    }

    override fun reset() {
        pendingToolkitRemovals.clear()
        refreshToolkitList()
        projectDirectoryPanel?.reset()
        super<BoundConfigurable>.reset()
    }

    override fun cancel() {
        // Cancelling a modified page does not run reset on this platform.
        pendingToolkitRemovals.clear()
        refreshToolkitList()
    }
}
