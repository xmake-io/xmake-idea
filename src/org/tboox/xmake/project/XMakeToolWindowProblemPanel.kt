package org.tboox.xmake.project

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.layout.panel
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

class XMakeToolWindowProblemPanel(project: Project) : SimpleToolWindowPanel(false) {

    // the toolbar
    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar("XMake Toolbar", actionManager.getAction("XMake.Menu") as DefaultActionGroup, false)
    }

    // the content
    val content = panel {
        row {
        }
    }

    init {

        // init toolbar
        setToolbar(toolbar.component)

        // init content
        setContent(content)
    }

    override fun getData(dataId: String): Any? {
        return super.getData(dataId)
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeToolWindowProblemPanel::class.java.getName())
    }
}
