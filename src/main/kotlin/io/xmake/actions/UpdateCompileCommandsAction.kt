package io.xmake.actions

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.project.xmakeConsoleView
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.SystemUtils
import io.xmake.utils.execute.SyncDirection
import io.xmake.utils.execute.syncFileByToolkit
import kotlinx.coroutines.GlobalScope

class UpdateCompileCommandsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // the project
        val project = e.project ?: return

        // clear console first
        project.xmakeConsoleView.clear()

        // configure and build it
        val xmakeConfiguration = project.xmakeConfiguration
        if (xmakeConfiguration.changed) {
            SystemUtils.runvInConsole(project, xmakeConfiguration.configurationCommandLine)
                ?.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(e: ProcessEvent) {
                    SystemUtils.runvInConsole(project, xmakeConfiguration.updateCompileCommansLine, false, true, true)
                        ?.addProcessListener(
                        object: ProcessAdapter() {
                            override fun processTerminated(e: ProcessEvent) {
                                syncFileByToolkit(GlobalScope, project, project.activatedToolkit!!, "compile_commands.json", SyncDirection.UPSTREAM_TO_LOCAL)
                                // Todo: Reload from disks after download from remote.
                            }
                        }
                    )
                }
            })
            xmakeConfiguration.changed = false
        } else {
            SystemUtils.runvInConsole(project, xmakeConfiguration.updateCompileCommansLine, false, true, true)
                ?.addProcessListener(
                object: ProcessAdapter() {
                    override fun processTerminated(e: ProcessEvent) {
                        syncFileByToolkit(GlobalScope, project, project.activatedToolkit!!, "compile_commands.json", SyncDirection.UPSTREAM_TO_LOCAL)
                    }
                }
            )
        }
    }
}