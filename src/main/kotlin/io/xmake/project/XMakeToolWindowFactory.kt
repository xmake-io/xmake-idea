/*!A Xmake integration in IntelliJ IDEA/Clion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2015-present, Xmake Open Source Community.
 *
 * @author      ruki
 * @file        XMakeToolWindowFactory.kt
 *
 */
package io.xmake.project

import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import io.xmake.shared.XMakeProblem
import io.xmake.utils.SystemUtils

class XMakeToolWindowFactory : ToolWindowFactory {

    override fun isApplicable(project: Project): Boolean {
        return SystemUtils.isXMakeProject(project)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {

        // add output tab/panel
        val toolwindowOutputPanel = XMakeToolWindowOutputPanel(project)
        val outputTab = ContentFactory.getInstance().createContent(toolwindowOutputPanel, "Output", false)
        toolWindow.contentManager.addContent(outputTab)

        // add problem tab/panel
        val toolwindowProblemPanel = XMakeToolWindowProblemPanel(project)
        val problemTab = ContentFactory.getInstance().createContent(toolwindowProblemPanel, "Problem", false)
        toolWindow.contentManager.addContent(problemTab)

        // show the output panel by default
        toolWindow.contentManager.setSelectedContent(outputTab)
    }
}

// the xmake tool windows
val Project.xmakeToolWindow: ToolWindow?
    get() = ToolWindowManager.getInstance(this).getToolWindow("XMake")

// the xmake output panel
val Project.xmakeOutputPanel: XMakeToolWindowOutputPanel
    get() = this.xmakeToolWindow?.contentManager?.getContent(0)?.component as XMakeToolWindowOutputPanel

// the xmake problem panel
val Project.xmakeProblemPanel: XMakeToolWindowProblemPanel
    get() = this.xmakeToolWindow?.contentManager?.getContent(1)?.component as XMakeToolWindowProblemPanel

// the xmake console view
val Project.xmakeConsoleView: ConsoleView
    get() = this.xmakeOutputPanel.consoleView

// the xmake problem list
var Project.xmakeProblemList: List<XMakeProblem>
    get() = this.xmakeProblemPanel.problems
    set(value) {
        this.xmakeProblemPanel.problems = value
    }
