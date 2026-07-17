/*!A Xmake integration in IntelliJ IDEA/Clion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2015-present, Xmake Open Source Community.
 *
 * @author      ruki
 * @file        QuickStartAction.kt
 *
 */
package io.xmake.actions

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindowManager
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.project.console.xmakeConsoleService
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.SystemUtils
import io.xmake.utils.exception.XMakeRunConfigurationNotSetException
import java.io.File

class QuickStartAction : XMakeProjectAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isVisible = true
        e.presentation.isEnabled = !SystemUtils.isXMakeProject(project)
    }

    override fun execute(project: Project) {

        if (!SystemUtils.isXMakeProject(project)) {
            val xmakePath = project.activatedToolkit?.path ?: "xmake"
            val commandLine = GeneralCommandLine(xmakePath, "create", "-P", ".")
            commandLine.workDirectory = File(project.basePath ?: return)

            try {
                val processHandler = OSProcessHandler(commandLine)
                processHandler.addProcessListener(object : ProcessListener {
                    override fun processTerminated(event: ProcessEvent) {
                        if (project.isDisposed) return

                        if (event.exitCode == 0) {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("XMake.NotificationGroup")
                                .createNotification("XMake project created successfully!", NotificationType.INFORMATION)
                                .notify(project)

                            ToolWindowManager.getInstance(project).invokeLater {
                                if (project.isDisposed) return@invokeLater

                                // Refresh VFS
                                project.basePath?.let { path ->
                                    val file = LocalFileSystem.getInstance().findFileByPath(path)
                                    file?.let {
                                        VfsUtil.markDirtyAndRefresh(false, true, true, it)
                                    }
                                }

                                // Show Tool Window
                                project.xmakeConsoleService.whenReady { console ->
                                    console.showOutput()
                                }
                            }
                        } else {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("XMake.NotificationGroup")
                                .createNotification("Failed to create XMake project.", NotificationType.ERROR)
                                .notify(project)
                        }
                    }
                })
                processHandler.startNotify()
            } catch (e: Exception) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("XMake.NotificationGroup")
                    .createNotification("Failed to start xmake create: ${e.message}", NotificationType.ERROR)
                    .notify(project)
            }
            return
        }

        project.xmakeConsoleService.whenReady { console ->
            // clear console first
            console.clear()

            try {
                // quick start
                SystemUtils.runvInConsole(project, console, project.xmakeConfiguration.quickStartCommandLine, true, false, true)
            } catch (e: XMakeRunConfigurationNotSetException) {
                console.print(
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
}
