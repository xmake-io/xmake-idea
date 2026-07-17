package io.xmake.actions

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import io.xmake.run.XMakeRunConfiguration
import io.xmake.run.XMakeRunner
import io.xmake.utils.SystemUtils

class DebugAction : XMakeBaseAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        if (!e.presentation.isEnabledAndVisible) return

        if (e.project == null) return

        // Disable if native debug is not available (grayed out)
        if (!SystemUtils.isNativeDebugAvailable()) {
            e.presentation.isEnabled = false
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val runManager = RunManager.getInstance(project)
        val selectedSettings = runManager.selectedConfiguration

        if (selectedSettings == null || selectedSettings.configuration !is XMakeRunConfiguration) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XMake.NotificationGroup")
                .createNotification("Please select a valid XMake configuration first!", NotificationType.WARNING)
                .notify(project)
            return
        }

        ProgramRunnerUtil.executeConfiguration(
            selectedSettings,
            DefaultDebugExecutor.getDebugExecutorInstance()
        )
    }
}
