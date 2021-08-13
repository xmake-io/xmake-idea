package io.xmake.actions

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.xmake.project.xmakeConsoleView
import io.xmake.utils.SystemUtils
import io.xmake.shared.xmakeConfiguration


class BuildAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {

        // the project
        val project = e.project ?: return

        // clear console first
        project.xmakeConsoleView.clear()

        // configure and build it
        val xmakeConfiguration = project.xmakeConfiguration
        if (xmakeConfiguration.changed) {
            SystemUtils.runvInConsole(project, xmakeConfiguration.configurationCommandLine).addProcessListener(object: ProcessAdapter() {
                override fun processTerminated(e: ProcessEvent) {
                    SystemUtils.runvInConsole(project, xmakeConfiguration.buildCommandLine, false, true, true)
                }
            })
            xmakeConfiguration.changed = false
        } else {
            SystemUtils.runvInConsole(project, xmakeConfiguration.buildCommandLine, true, true, true)
        }
    }
}
