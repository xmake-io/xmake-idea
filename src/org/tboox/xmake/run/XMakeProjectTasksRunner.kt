package org.tboox.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.task.*

class XMakeProjectTasksRunner : ProjectTaskRunner() {

    override fun run(project: Project, context: ProjectTaskContext, callback: ProjectTaskNotification?, tasks: MutableCollection<out ProjectTask>) {
        Log.info("run")
    }

    override fun canRun(projectTask: ProjectTask): Boolean {
        Log.info("canRun")
        return true
    }

    override fun createExecutionEnvironment(project: Project, task: ExecuteRunConfigurationTask, executor: Executor?): ExecutionEnvironment? = null

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeProjectTasksRunner::class.java.getName())
    }
}
