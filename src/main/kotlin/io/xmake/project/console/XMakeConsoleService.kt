package io.xmake.project.console

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import io.xmake.shared.XMakeProblem

class XMakeConsole internal constructor(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val outputPanel: XMakeToolWindowOutputPanel,
    private val problemPanel: XMakeToolWindowProblemPanel,
    private val outputContent: Content
) {
    internal val view: ConsoleView
        get() = outputPanel.consoleView

    fun clear() {
        view.clear()
    }

    fun print(text: String, contentType: ConsoleViewContentType) {
        view.print(text, contentType)
    }

    fun showOutput() {
        ToolWindowManager.getInstance(project).invokeLater {
            if (project.isDisposed) return@invokeLater

            toolWindow.show {
                if (!project.isDisposed) {
                    toolWindow.contentManager.setSelectedContent(outputContent)
                }
            }
        }
    }

    fun updateProblems(problems: List<XMakeProblem>) {
        ToolWindowManager.getInstance(project).invokeLater {
            if (!project.isDisposed) {
                problemPanel.problems = problems
            }
        }
    }
}

@Service(Service.Level.PROJECT)
class XMakeConsoleService(private val project: Project) {
    private var console: XMakeConsole? = null

    val currentConsole: XMakeConsole
        get() = checkNotNull(console) { "XMake console has not been initialized" }

    internal fun register(
        toolWindow: ToolWindow,
        outputPanel: XMakeToolWindowOutputPanel,
        problemPanel: XMakeToolWindowProblemPanel,
        outputContent: Content
    ) {
        check(ApplicationManager.getApplication().isDispatchThread)
        console = XMakeConsole(project, toolWindow, outputPanel, problemPanel, outputContent)
    }

}

val Project.xmakeConsoleService: XMakeConsoleService
    get() = getService(XMakeConsoleService::class.java)
