package org.tboox.xmake.project

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.panel
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.ListSelectionModel

class XMakeToolWindow(
        private val project: Project
) {

    // the toolbar
    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar("XMake Toolbar", actionManager.getAction("XMake.Menu") as DefaultActionGroup, true)
    }

    // the content
    val content = panel {
        row {
        }
    }

    // to string
    override fun toString(): String {
        return "XMakeToolWindow()"
    }
}
