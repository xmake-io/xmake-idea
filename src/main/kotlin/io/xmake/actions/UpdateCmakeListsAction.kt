package io.xmake.actions

import com.intellij.execution.RunManager
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.vfs.LocalFileSystem
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.project.xmakeConsoleView
import io.xmake.run.XMakeRunConfiguration
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.SystemUtils
import io.xmake.utils.execute.SyncDirection
import io.xmake.utils.execute.syncFileByToolkit
import kotlinx.coroutines.GlobalScope

class UpdateCmakeListsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // the project
        val project = e.project ?: return

        // clear console first
        project.xmakeConsoleView.clear()

        // configure and build it
        val xmakeConfiguration = project.xmakeConfiguration
        if (xmakeConfiguration.changed) {
            SystemUtils.runvInConsole(project, xmakeConfiguration.configurationCommandLine).addProcessListener(object: ProcessAdapter() {
                override fun processTerminated(e: ProcessEvent) {
                    SystemUtils.runvInConsole(project, xmakeConfiguration.updateCmakeListsCommandLine, false, true, true).addProcessListener(
                        object: ProcessAdapter() {
                            override fun processTerminated(e: ProcessEvent) {
                                syncFileByToolkit(GlobalScope, project, project.activatedToolkit!!, "CMakeLists.txt", SyncDirection.UPSTREAM_TO_LOCAL)
                                // Todo: Reload from disks after download from remote.
                            }
                        }
                    )
                }
            })
            xmakeConfiguration.changed = false
        } else {
            SystemUtils.runvInConsole(project, xmakeConfiguration.updateCmakeListsCommandLine, false, true, true).addProcessListener(
                object: ProcessAdapter() {
                    override fun processTerminated(e: ProcessEvent) {
                        syncFileByToolkit(GlobalScope, project, project.activatedToolkit!!, "CMakeLists.txt", SyncDirection.UPSTREAM_TO_LOCAL)
                    }
                }
            )
        }
    }
}