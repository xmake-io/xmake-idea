package org.tboox.xmake.run

import com.intellij.execution.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.task.*
import com.intellij.task.ExecuteRunConfigurationTask
import org.tboox.xmake.project.xmakeConsoleView
import org.tboox.xmake.utils.SystemUtils
import org.tboox.xmake.shared.xmakeConfiguration

class XMakeProjectTasksRunner : ProjectTaskRunner() {

    override fun run(project: Project, context: ProjectTaskContext, callback: ProjectTaskNotification?, tasks: MutableCollection<out ProjectTask>) {

        // clear console first
        project.xmakeConsoleView.clear()

        // configure it first
        val xmakeConfiguration = project.xmakeConfiguration
        if (xmakeConfiguration.changed) {
            SystemUtils.runvInConsole(project, xmakeConfiguration.configurationCommandLine)
            xmakeConfiguration.changed = false
        }

        // build it
        SystemUtils.runvInConsole(project, xmakeConfiguration.buildCommandLine)
    }

    override fun canRun(projectTask: ProjectTask): Boolean {

        // hook 'Build Project/Module F9' => build
        if (projectTask is ModuleBuildTask) {
            return true
        }

        /*
        // hook 'Build Artifacts' => package
        if (projectTask is ArtifactBuildTask) {
            return true
        }

        // run configuration?
        if (projectTask is ExecuteRunConfigurationTask) {
            val runProfile = projectTask.runProfile
            if (runProfile is XMakeRunConfiguration) {
                return true
            }
        }*/

        return false
    }

    override fun createExecutionEnvironment(project: Project, task: ExecuteRunConfigurationTask, executor: Executor?): ExecutionEnvironment? = null

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeProjectTasksRunner::class.java.getName())
    }
}
