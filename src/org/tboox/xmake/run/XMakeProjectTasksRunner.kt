package org.tboox.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.task.*
import com.intellij.task.ExecuteRunConfigurationTask
import com.intellij.execution.configurations.RunProfile



class XMakeProjectTasksRunner : ProjectTaskRunner() {

    override fun run(project: Project, context: ProjectTaskContext, callback: ProjectTaskNotification?, tasks: MutableCollection<out ProjectTask>) {
        Log.info("run")
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
