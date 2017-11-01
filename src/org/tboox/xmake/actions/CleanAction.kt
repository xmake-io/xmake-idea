package org.tboox.xmake.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.tboox.xmake.project.XMakeProjectConfiguration
import org.tboox.xmake.utils.SystemUtils

class CleanAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectConfiguration = project.getComponent(XMakeProjectConfiguration::class.java)
        SystemUtils.runvInConsole(project, "Clean Targets", projectConfiguration.cleanCommandLine)
    }
}
