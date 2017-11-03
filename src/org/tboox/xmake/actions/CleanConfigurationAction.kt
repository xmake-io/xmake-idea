package org.tboox.xmake.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.tboox.xmake.project.xmakeConsoleView
import org.tboox.xmake.utils.SystemUtils
import org.tboox.xmake.shared.xmakeConfiguration

class CleanConfigurationAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {

        // the project
        val project = e.project ?: return

        // clear console first
        project.xmakeConsoleView.clear()

        // clear configure
        val xmakeConfiguration = project.xmakeConfiguration
        SystemUtils.runvInConsole(project, xmakeConfiguration.cleanConfigurationCommandLine)
        xmakeConfiguration.changed = false
    }
}
