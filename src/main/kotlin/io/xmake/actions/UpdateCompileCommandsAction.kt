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
 * @file        UpdateCompileCommandsAction.kt
 *
 */
package io.xmake.actions

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFileManager
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.project.xmakeConsoleView
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.SystemUtils
import io.xmake.utils.exception.XMakeRunConfigurationNotSetException
import io.xmake.utils.execute.fetchGeneratedFile
import io.xmake.utils.execute.syncBeforeFetch

class UpdateCompileCommandsAction : AnAction() {
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
                    ?.addProcessListener(object : ProcessListener {
                        override fun processTerminated(e: ProcessEvent) {
                            project.activatedToolkit?.let { syncBeforeFetch(project, it) }

                            SystemUtils.runvInConsole(
                                project,
                                xmakeConfiguration.updateCompileCommandsLine,
                                false,
                                true,
                                true
                            )
                                ?.addProcessListener(
                                    object : ProcessListener {
                                        override fun processTerminated(e: ProcessEvent) {
                                            val toolkit = project.activatedToolkit
                                            if (toolkit != null) {
                                                fetchGeneratedFile(project, toolkit, "compile_commands.json")
                                            } else {
                                                ApplicationManager.getApplication().invokeLater {
                                                    runWriteAction {
                                                        VirtualFileManager.getInstance().syncRefresh()
                                                    }
                                                }
                                            }
                                            // Todo: Reload from disks after download from remote.
                                        }
                                    }
                                )
                        }
                    })
                xmakeConfiguration.changed = false
            } else {
                SystemUtils.runvInConsole(project, xmakeConfiguration.updateCompileCommandsLine, false, true, true)
                    ?.addProcessListener(
                        object : ProcessListener {
                            override fun processTerminated(e: ProcessEvent) {
                                val toolkit = project.activatedToolkit
                                if (toolkit != null) {
                                    fetchGeneratedFile(project, toolkit, "compile_commands.json")
                                } else {
                                    ApplicationManager.getApplication().invokeLater {
                                        runWriteAction {
                                            VirtualFileManager.getInstance().syncRefresh()
                                        }
                                    }
                                }
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
                .getNotificationGroup("XMake.NotificationGroup")
                .createNotification("Error with XMake Configuration", e.message ?: "", NotificationType.ERROR)
                .notify(project)
        } catch (e: ExecutionException) {
            project.xmakeConsoleView.print(
                "An error occurred during update: ${e.message}\n",
                ConsoleViewContentType.ERROR_OUTPUT
            )
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XMake.NotificationGroup")
                .createNotification("Error with XMake Update", e.message ?: "", NotificationType.ERROR)
                .notify(project)
        }
    }
}
