package io.xmake.project.console

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Log = logger<XMakeConsoleManager>()

class XMakeConsole internal constructor(
    private val project: Project,
    private val toolWindow: ToolWindow,
    private val outputPanel: XMakeToolWindowOutputPanel,
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
        if (project.isDisposed) return

        ToolWindowManager.getInstance(project).invokeLater {
            if (project.isDisposed) return@invokeLater

            toolWindow.show {
                if (!project.isDisposed) {
                    toolWindow.contentManager.setSelectedContent(outputContent)
                }
            }
        }
    }
}

@Service(Service.Level.PROJECT)
class XMakeConsoleManager(private val project: Project) {
    private var console: XMakeConsole? = null

    internal fun register(
        toolWindow: ToolWindow,
        outputPanel: XMakeToolWindowOutputPanel,
        outputContent: Content
    ) {
        check(ApplicationManager.getApplication().isDispatchThread)
        console = XMakeConsole(project, toolWindow, outputPanel, outputContent)
    }

    fun whenReady(
        onUnavailable: (Throwable) -> Unit = { Log.warn("XMake console is unavailable", it) },
        action: (XMakeConsole) -> Unit
    ) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            runWhenReady(onUnavailable, action)
            return
        }

        if (project.isDisposed) {
            onUnavailable(IllegalStateException("Project was disposed before the XMake console was initialized"))
            return
        }

        val toolWindowManager = ToolWindowManager.getInstance(project)
        toolWindowManager.invokeLater {
            runWhenReady(onUnavailable, action)
        }
    }

    suspend fun awaitReady(): XMakeConsole = withContext(Dispatchers.EDT) {
        console ?: createConsole()
    }

    private fun runWhenReady(
        onUnavailable: (Throwable) -> Unit,
        action: (XMakeConsole) -> Unit
    ) {
        try {
            action(console ?: createConsole())
        } catch (error: Throwable) {
            onUnavailable(error)
        }
    }

    private fun createConsole(): XMakeConsole {
        check(ApplicationManager.getApplication().isDispatchThread)
        check(!project.isDisposed) { "Project was disposed before the XMake console was initialized" }

                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(XMAKE_TOOL_WINDOW_ID)
            ?: error("XMake tool window is not registered")

        // Accessing the content manager initializes descriptor-owned tool window content.
        toolWindow.contentManager
        return console ?: error("XMake console was not initialized")
    }

    companion object {
        fun getInstance(project: Project): XMakeConsoleManager =
            project.serviceOrNull() ?: error("Failed to get XMakeConsoleManager for $project")
    }
}
