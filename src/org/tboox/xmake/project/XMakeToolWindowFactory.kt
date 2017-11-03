package org.tboox.xmake.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class XMakeToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        // add output tab/panel
        val toolwindowOutputPanel = XMakeToolWindowOutputPanel(project)
        val outputTab = ContentFactory.SERVICE.getInstance().createContent(toolwindowOutputPanel, "Output", false)
        toolWindow.contentManager.addContent(outputTab)

        // add problem tab/panel
        val toolwindowProblemPanel = XMakeToolWindowProblemPanel(project)
        val problemTab = ContentFactory.SERVICE.getInstance().createContent(toolwindowProblemPanel, "Problem", false)
        toolWindow.contentManager.addContent(problemTab)
    }
}
