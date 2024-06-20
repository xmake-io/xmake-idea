package io.xmake.project

import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel

class XMakeToolWindowOutputPanel(// the project
    val project: Project
) : SimpleToolWindowPanel(false) {

    // the toolbar
    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar(
            "XMake Toolbar",
            actionManager.getAction("XMake.Menu") as DefaultActionGroup,
            false
        )
    }

    // the console view
    val consoleView: ConsoleView = run {
        val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        builder.setViewer(true)
        builder.console
    }

    init {

        // init toolbar
        setToolbar(toolbar.component)
        toolbar.targetComponent = this

        // init content
        setContent(consoleView.component)
    }

    // show panel
    fun showPanel() {
        val contentManager = project.xmakeToolWindow?.contentManager
        contentManager?.setSelectedContent(contentManager.getContent(0)!!)
    }

    override fun getData(dataId: String): Any? {
        return super.getData(dataId)
    }

    companion object {
        private val Log = Logger.getInstance(XMakeToolWindowOutputPanel::class.java.getName())
    }
}
