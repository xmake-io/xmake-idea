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
 * @file        RebuildAction.kt
 *
 */
package io.xmake.actions

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.ExecutionException
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import io.xmake.project.console.XMakeConsole
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.SystemUtils
import io.xmake.utils.exception.XMakeRunConfigurationNotSetException

class RebuildAction : XMakeConsoleAction() {

    override fun update(e: AnActionEvent) {
        super.update(e)
        if (!e.presentation.isEnabledAndVisible) return
        val project = e.project ?: return
        if (SystemUtils.isXMakeExecutableConfigSelected(project)) {
            e.presentation.isEnabledAndVisible = false
        }
    }

    override fun execute(project: Project, console: XMakeConsole) {

        // clear console first
        console.clear()

        try {
            // configure and rebuild it
            val xmakeConfiguration = project.xmakeConfiguration
            if (xmakeConfiguration.changed) {
                SystemUtils.runvInConsole(project, console, xmakeConfiguration.configurationCommandLine)
                    ?.addProcessListener(object : ProcessListener {
                        override fun processTerminated(e: ProcessEvent) {
                            SystemUtils.runvInConsole(project, console, xmakeConfiguration.rebuildCommandLine, false, true, true)
                        }
                    })
                xmakeConfiguration.changed = false
            } else {
                SystemUtils.runvInConsole(project, console, xmakeConfiguration.rebuildCommandLine, true, true, true)
            }
        } catch (e: XMakeRunConfigurationNotSetException) {
            console.print(
                "Please select a xmake run configuration first!\n",
                ConsoleViewContentType.ERROR_OUTPUT
            )
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XMake.NotificationGroup")
                .createNotification("Error with XMake Configuration", e.message ?: "", NotificationType.ERROR)
                .notify(project)
        } catch (e: ExecutionException) {
            console.print(
                "An error occurred during rebuild: ${e.message}\n",
                ConsoleViewContentType.ERROR_OUTPUT
            )
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XMake.NotificationGroup")
                .createNotification("Error with XMake Rebuild", e.message ?: "", NotificationType.ERROR)
                .notify(project)
        }
    }
}
