package io.xmake.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import io.xmake.utils.SystemUtils

class XMakeMenuGroup : DefaultActionGroup() {
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
