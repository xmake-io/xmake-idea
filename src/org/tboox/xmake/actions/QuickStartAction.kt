package org.tboox.xmake.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.tboox.xmake.utils.SystemUtils
import org.tboox.xmake.shared.xmakeConfiguration

class QuickStartAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SystemUtils.runvInConsole(project, project.xmakeConfiguration.quickStartCommandLine)
    }
}
