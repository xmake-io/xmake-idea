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

import com.intellij.execution.RunManager
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import io.xmake.project.console.XMakeConsole
import io.xmake.project.console.xmakeConsoleService
import io.xmake.run.XMakeRunConfiguration
import io.xmake.run.command.XMakeCommandFactory
import io.xmake.run.command.xmakeExecutionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

abstract class XMakeCommandAction : XMakeProjectAction() {

    final override fun execute(project: Project) {
        val configuration = RunManager.getInstance(project)
            .selectedConfiguration
            ?.configuration as? XMakeRunConfiguration
        val commandsResult = configuration?.let { runCatching { XMakeCommandFactory(it) } }
        FileDocumentManager.getInstance().saveAllDocuments()

        project.xmakeExecutionService.submit {
            val console = project.xmakeConsoleService.awaitReady()
            withContext(Dispatchers.EDT) {
                console.clear()
                if (commandsResult == null) {
                    console.reportMissingRunConfiguration(project)
                }
            }
            if (commandsResult == null) {
                return@submit
            }

            execute(project, console, commandsResult.getOrThrow())
        }.invokeOnCompletion { error ->
            if (error == null || error is CancellationException || project.isDisposed) {
                return@invokeOnCompletion
            }
            project.xmakeConsoleService.whenReady { console ->
                console.reportCommandError(project, error)
            }
        }
    }

    internal abstract suspend fun execute(
        project: Project,
        console: XMakeConsole,
        commands: XMakeCommandFactory,
    )
}

private fun XMakeConsole.reportMissingRunConfiguration(project: Project) {
    print("Please select an XMake run configuration first!\n", ConsoleViewContentType.ERROR_OUTPUT)
    notifyError(project, "Error with XMake Configuration", "XMake configuration is not selected!")
}

private fun XMakeConsole.reportCommandError(project: Project, error: Throwable) {
    print(
        "XMake command failed: ${error.message}\n",
        ConsoleViewContentType.ERROR_OUTPUT,
    )
    notifyError(project, "XMake Command Failed", error.message.orEmpty())
}

private fun notifyError(project: Project, title: String, content: String) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup("XMake.NotificationGroup")
        .createNotification(title, content, NotificationType.ERROR)
        .notify(project)
}
