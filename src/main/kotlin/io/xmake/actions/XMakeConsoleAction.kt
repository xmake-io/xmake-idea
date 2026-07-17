package io.xmake.actions

import com.intellij.openapi.project.Project
import io.xmake.project.console.XMakeConsole
import io.xmake.project.console.xmakeConsoleService

abstract class XMakeConsoleAction : XMakeProjectAction() {

    final override fun execute(project: Project) {
        project.xmakeConsoleService.whenReady { console ->
            execute(project, console)
        }
    }

    protected abstract fun execute(project: Project, console: XMakeConsole)
}
