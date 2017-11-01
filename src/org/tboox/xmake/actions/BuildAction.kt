package org.tboox.xmake.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import org.tboox.xmake.project.XMakeProjectConfiguration
import org.tboox.xmake.utils.SystemUtils

class BuildAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val projectConfiguration = project.getComponent(XMakeProjectConfiguration::class.java)
        SystemUtils.runvInConsole(project, "Build Project", projectConfiguration.buildCommandLine)
    }

    // the log
    private val Log = Logger.getInstance(BuildAction::class.java.getName())

}
