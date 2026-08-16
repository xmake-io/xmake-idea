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
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.wm.ToolWindowManager
import io.xmake.project.console.xmakeConsoleService
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.utils.SystemUtils
import io.xmake.utils.execute.createProcess
import io.xmake.utils.path.WorkingDirectoryResolver

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

        FileDocumentManager.getInstance().saveAllDocuments()

        if (!SystemUtils.isXMakeProject(project)) {
            val manager = ToolkitManager.getInstance()
            val registeredToolkits = manager.registeredToolkits(project)
            val toolkit = manager.defaultToolkitId
                ?.let { id -> registeredToolkits.firstOrNull { toolkit -> toolkit.id == id } }
                ?.takeUnless(Toolkit::requiresBackend)
                ?: registeredToolkits.firstOrNull { !it.requiresBackend }
                ?: Toolkit(path = "xmake")
            val projectPath = project.basePath ?: return
            val workingDirectory = WorkingDirectoryResolver.resolve(project, projectPath, toolkit)
            val commandLine = GeneralCommandLine(toolkit.path, "create", "-P", ".")
                .withWorkDirectory(workingDirectory)

            ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val process = commandLine.createProcess(toolkit)
                    val processHandler = KillableColoredProcessHandler(
                        process,
                        commandLine.commandLineString,
                        Charsets.UTF_8,
                    )
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
                    notifyQuickStartFailure(project, "Failed to start xmake create: ${e.message}")
                }
            }
        }
    }

    private fun notifyQuickStartFailure(project: Project, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("XMake.NotificationGroup")
            .createNotification(message, NotificationType.ERROR)
            .notify(project)
    }
}
