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

import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import io.xmake.utils.notify
import io.xmake.project.console.XMakeConsoleManager
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import io.xmake.project.directory.hasRootXMakeLuaCached
import io.xmake.project.directory.hasXMakeProjectDirectorySource
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.run.command.xmakeExecutor
import io.xmake.utils.cli.xmake
import io.xmake.utils.execute.ProcessTimeoutException
import io.xmake.utils.execute.failureDetail
import io.xmake.utils.path.WorkingDirectoryResolver
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException

class QuickStartAction : XMakeProjectAction() {

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        e.presentation.isVisible = !project.hasXMakeProjectDirectorySource
        e.presentation.isEnabled = !project.hasRootXMakeLuaCached
    }

    override fun execute(project: Project) {
        FileDocumentManager.getInstance().saveAllDocuments()
        if (project.hasRootXMakeLuaCached) return

        val toolkit = ToolkitManager.getInstance().defaultRunnableToolkit(project)
        val projectDirectoryPath = project.basePath ?: return
        val resolvedProjectDirectoryPath = WorkingDirectoryResolver.resolve(project, projectDirectoryPath, toolkit)
        val commandLine = xmake(toolkit).create(".").apply {
            withWorkDirectory(resolvedProjectDirectoryPath)
        }

        project.xmakeExecutor.submit {
            try {
                val result = commandLine.execute(project, CREATE_TIMEOUT)
                if (result.exitCode == 0) {
                    onQuickStartCreated(project)
                } else {
                    project.notify(
                        "xmake create failed (exit=${result.exitCode}): " +
                                failureDetail((result.stdOut + result.stdErr).trim()),
                    )
                }
            } catch (e: ProcessTimeoutException) {
                project.notify("xmake create timed out and was terminated.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                project.notify("Failed to start xmake create: ${e.message}")
            }
        }
    }

    private suspend fun onQuickStartCreated(project: Project) {
        project.notify("XMake project created successfully!", NotificationType.INFORMATION)
        project.basePath
            ?.let { withContext(Dispatchers.IO) { LocalFileSystem.getInstance().findFileByPath(it) } }
            ?.let { VfsUtil.markDirtyAndRefresh(true, true, true, it) }
        if (!project.isDisposed) {
            XMakeConsoleManager.getInstance(project).awaitReady().showOutput()
        }
    }

    private companion object {
        private val CREATE_TIMEOUT = 120.seconds
    }
}
