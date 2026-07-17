package io.xmake.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import io.xmake.utils.SystemUtils

abstract class XMakeProjectAction : AnAction() {

    final override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        execute(project)
    }

    protected abstract fun execute(project: Project)

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
