package org.tboox.xmake.actions

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.tboox.xmake.utils.SystemUtils
import org.tboox.xmake.shared.xmakeConfiguration
import org.tboox.xmake.utils.ConsoleProcessHandler
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.project.Project
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import org.tboox.xmake.utils.ExternalToolRunner
import org.tboox.xmake.utils.TerminalExecutionConsole


class BuildAction : AnAction() {


    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        //SystemUtils.runvInConsole(project, "Build Project", project.xmakeConfiguration.buildCommandLine)

        /*
        val consoleView = createConsoleView(project)
        consoleView.print("hello\n", ConsoleViewContentType.NORMAL_OUTPUT)

        consoleView.print("hello\n", ConsoleViewContentType.NORMAL_OUTPUT)
        consoleView.print("hello\n", ConsoleViewContentType.NORMAL_OUTPUT)
        consoleView.print("hello\n", ConsoleViewContentType.NORMAL_OUTPUT)
        consoleView.print("hello\n", ConsoleViewContentType.NORMAL_OUTPUT)
        */

        //ExternalToolRunner(project, "xxxx", project.xmakeConfiguration.buildCommandLine).start()

        /*
        val consoleView = ExternalToolRunner(project, "xxxx", project.xmakeConfiguration.buildCommandLine).initConsoleUi()
        ConsoleProcessHandler(consoleView, project.xmakeConfiguration.buildCommandLine).startNotify()
        */

        val handler = OSProcessHandler(project.xmakeConfiguration.buildCommandLine)
        val consoleView = TerminalExecutionConsole(project, handler)


        ConsoleProcessHandler(consoleView, project.xmakeConfiguration.buildCommandLine).startNotify()
    }

    /*
    fun createConsoleView(project: Project): ConsoleViewImpl {
        val result = createConsoleView(project, false)

        return result
    }

    fun createConsoleView(project: Project, viewer: Boolean): ConsoleViewImpl {
        val result = ConsoleViewImpl(project, viewer)
        // next method inits editor in order to fix MPS-11721
//        result.component
        return result
    }*/

    fun createConsoleView(project: Project): ConsoleView {
        val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        builder.setViewer(true)
        return builder.console
    }
}
