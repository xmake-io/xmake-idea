package io.xmake.actions

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import io.xmake.debug.XMakeDebugSupport

class DebugAction : XMakeProjectAction() {

    override fun execute(project: Project) {
        if (!XMakeDebugSupport.isAvailable()) {
            Messages.showErrorDialog(
                project,
                "Debugging is unavailable: the CLion debugger integration is not available in this IDE.",
                "XMake Debug",
            )
            return
        }
        launchSelectedXMakeRunConfiguration(project, DefaultDebugExecutor.getDebugExecutorInstance())
    }
}
