package io.xmake.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.xmake.utils.SystemUtils

abstract class XMakeBaseAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project
        val presentation = e.presentation
        
        if (project == null) {
            presentation.isEnabledAndVisible = false
            return
        }
        
        presentation.isEnabledAndVisible = SystemUtils.isXMakeProject(project)
    }
}
