package io.xmake.actions

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import io.xmake.debug.XMakeDebugSupport

class DebugAction : XMakeProjectAction() {

    override fun execute(project: Project) {
        launchSelectedXMakeRunConfiguration(project, DefaultDebugExecutor.getDebugExecutorInstance())
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        if (!e.presentation.isEnabledAndVisible) return

        if (e.project == null) return

        if (!XMakeDebugSupport.isAvailable()) {
            e.presentation.isEnabled = false
        }
    }
}
