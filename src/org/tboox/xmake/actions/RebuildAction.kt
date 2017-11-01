package org.tboox.xmake.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.tboox.xmake.shared.XMakeConfiguration
import org.tboox.xmake.utils.SystemUtils

class RebuildAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        SystemUtils.runvInConsole(project, "Rebuild Project", XMakeConfiguration.rebuildCommandLine)
    }
}
