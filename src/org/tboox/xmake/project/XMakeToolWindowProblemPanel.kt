package org.tboox.xmake.project

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import org.tboox.xmake.icons.XMakeIcons
import javax.swing.JEditorPane
import javax.swing.JScrollPane
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class XMakeToolWindowProblemPanel(project: Project) : SimpleToolWindowPanel(false) {

    // the problems
    private var _problems: List<String> = emptyList()
    var problems: List<String>
        get() = _problems
        set(value) {
            check(ApplicationManager.getApplication().isDispatchThread)
            _problems = value
            problemList.setListData(problems.toTypedArray())
        }

    // the toolbar
    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar("XMake Toolbar", actionManager.getAction("XMake.Menu") as DefaultActionGroup, false)
    }

    // the problem list
    private val problemList = JBList<String>(emptyList()).apply {
        emptyText.text = "There are no compiling problems to display."
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : ColoredListCellRenderer<String>() {
            override fun customizeCellRenderer(list: JList<out String>, value: String, index: Int, selected: Boolean, hasFocus: Boolean) {
                if (index > 2) {
                    icon = XMakeIcons.ERROR
                } else {
                    icon = XMakeIcons.WARNING
                }
                var attrs = SimpleTextAttributes.REGULAR_ATTRIBUTES
                //attrs = attrs.derive(SimpleTextAttributes.STYLE_WAVED, null, null, JBColor.RED)
//                toolTipText = "xxxx is up-to-date"
                append(value, attrs)
            }
        }
    }

    // the problem pane
    val problemPane = JScrollPane(problemList).apply {
        border = null
    }

    // the content
    val content = panel {
        row {
            problemPane(CCFlags.push, CCFlags.grow)

        }
    }

    init {

        // init toolbar
        setToolbar(toolbar.component)
        toolbar.setTargetComponent(this)

        // init content
        setContent(content)
    }

    override fun getData(dataId: String): Any? {
        return super.getData(dataId)
    }

    companion object {
        private val Log = Logger.getInstance(XMakeToolWindowProblemPanel::class.java.getName())
    }
}
