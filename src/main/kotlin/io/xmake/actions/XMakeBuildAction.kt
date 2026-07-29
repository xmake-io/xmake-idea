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
 */
package io.xmake.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import io.xmake.build.XMakeBuildTask
import io.xmake.run.XMakeRunConfiguration
import io.xmake.run.command.XMakeCommandFactory

abstract class XMakeBuildAction : XMakeProjectAction() {
    final override fun execute(project: Project) {
        val configuration = project.selectedXMakeRunConfiguration
        if (configuration == null) {
            notifyConfigurationError(project, "Please select an XMake run configuration first")
            return
        }

        val task = try {
            configuration.checkConfiguration()
            createTask(project, configuration, XMakeCommandFactory(configuration))
        } catch (error: Exception) {
            notifyConfigurationError(project, error.message ?: "The XMake configuration is invalid")
            return
        }

        FileDocumentManager.getInstance().saveAllDocuments()
        ProjectTaskManager.getInstance(project).run(task)
    }

    internal abstract fun createTask(
        project: Project,
        configuration: XMakeRunConfiguration,
        commands: XMakeCommandFactory,
    ): XMakeBuildTask
}

private fun notifyConfigurationError(project: Project, message: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("XMake.NotificationGroup")
        .createNotification(message, NotificationType.ERROR)
        .notify(project)
}
