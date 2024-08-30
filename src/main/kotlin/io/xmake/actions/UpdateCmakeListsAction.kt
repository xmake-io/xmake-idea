package io.xmake.actions

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.project.xmakeConsoleView
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.SystemUtils
import io.xmake.utils.exception.XMakeRunConfigurationNotSetException
import io.xmake.utils.execute.fetchGeneratedFile
import io.xmake.utils.execute.syncBeforeFetch

class UpdateCmakeListsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // the project
        val project = e.project ?: return

        // clear console first
        project.xmakeConsoleView.clear()

        try {
            // configure and build it
            val xmakeConfiguration = project.xmakeConfiguration
            if (xmakeConfiguration.changed) {
                SystemUtils.runvInConsole(project, xmakeConfiguration.configurationCommandLine)
                    ?.addProcessListener(object : ProcessAdapter() {
                        override fun processTerminated(e: ProcessEvent) {
                            syncBeforeFetch(project, project.activatedToolkit!!)

                            SystemUtils.runvInConsole(
                                project,
                                xmakeConfiguration.updateCmakeListsCommandLine,
                                false,
                                true,
                                true
                            )?.addProcessListener(
                                object : ProcessAdapter() {
                                    override fun processTerminated(e: ProcessEvent) {
                                        fetchGeneratedFile(project, project.activatedToolkit!!, "CMakeLists.txt")
                                        // Todo: Reload from disks after download from remote.
                                    }
                                }
                            )
                        }
                    })
                xmakeConfiguration.changed = false
            } else {
                SystemUtils.runvInConsole(project, xmakeConfiguration.updateCmakeListsCommandLine, false, true, true)
                    ?.addProcessListener(
                        object : ProcessAdapter() {
                            override fun processTerminated(e: ProcessEvent) {
                                fetchGeneratedFile(project, project.activatedToolkit!!, "CMakeLists.txt")
                            }
                        }
                    )
            }
        } catch (e: XMakeRunConfigurationNotSetException) {
            project.xmakeConsoleView.print(
                "Please select a xmake run configuration first!\n",
                ConsoleViewContentType.ERROR_OUTPUT
            )
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XMake")
                .createNotification("Error with XMake Configuration", e.message ?: "", NotificationType.ERROR)
                .notify(project)
        }
    }
}