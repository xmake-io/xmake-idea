package io.xmake.actions

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import io.xmake.run.XMakeRunConfiguration
import io.xmake.run.XMakeRunner
import io.xmake.utils.SystemUtils

class DebugAction : XMakeProjectAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        if (!e.presentation.isEnabledAndVisible) return

        val project = e.project ?: return

        // Hide when a native "Xmake Executable" config is selected; CLion drives that build/run/debug natively.
        if (SystemUtils.isXMakeExecutableConfigSelected(project)) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        // Disable if native debug is not available (grayed out)
        if (!SystemUtils.isNativeDebugAvailable()) {
            e.presentation.isEnabled = false
        }
    }

    override fun execute(project: Project) {
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
