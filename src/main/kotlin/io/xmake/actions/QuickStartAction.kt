package io.xmake.actions

import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.xmake.project.xmakeConsoleView
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.SystemUtils
import io.xmake.utils.exception.XMakeRunConfigurationNotSetException

class QuickStartAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {

        // the project
        val project = e.project ?: return

        // clear console first
        project.xmakeConsoleView.clear()

        try {
            // quick start
            SystemUtils.runvInConsole(project, project.xmakeConfiguration.quickStartCommandLine, true, false, true)
        } catch (e: XMakeRunConfigurationNotSetException) {
            project.xmakeConsoleView.print(
                "Please select a xmake run configuration first!\n",
                ConsoleViewContentType.ERROR_OUTPUT
            )
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XMake.NotificationGroup")
                .createNotification("Error with XMake Configuration", e.message ?: "", NotificationType.ERROR)
                .notify(project)
        }

    }
}
