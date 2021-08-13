package io.xmake.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.xmake.project.xmakeConsoleView
import io.xmake.utils.SystemUtils
import io.xmake.shared.xmakeConfiguration

class QuickStartAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {

        // the project
        val project = e.project ?: return

        // clear console first
        project.xmakeConsoleView.clear()

        // quick start
        SystemUtils.runvInConsole(project, project.xmakeConfiguration.quickStartCommandLine, true, false, true)
    }
}
