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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import io.xmake.build.XMakeBuildTask
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.run.command.XMakeCommandFactory
import io.xmake.run.target.activeOrSingleXMakeBuildProfile

abstract class XMakeBuildAction : XMakeProjectAction() {
    final override fun execute(project: Project) {
        val profile = project.activeOrSingleXMakeBuildProfile
        if (profile == null) {
            notifyConfigurationError(project, "Please select an XMake build profile first")
            return
        }

        FileDocumentManager.getInstance().saveAllDocuments()
        ApplicationManager.getApplication().executeOnPooledThread {
            val task = try {
                createTask(project, profile, XMakeCommandFactory(project, profile))
            } catch (error: Exception) {
                notifyConfigurationError(project, error.message ?: "The XMake build profile is invalid")
                return@executeOnPooledThread
            }
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    ProjectTaskManager.getInstance(project).run(task)
                }
            }
        }
    }

    internal abstract fun createTask(
        project: Project,
        profile: XMakeBuildProfile,
        commandFactory: XMakeCommandFactory,
    ): XMakeBuildTask
}

private fun notifyConfigurationError(project: Project, message: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("XMake.NotificationGroup")
        .createNotification(message, NotificationType.ERROR)
        .notify(project)
}
