package io.xmake.run

import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.AsyncProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.executeState
import com.intellij.execution.ui.RunContentDescriptor
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

abstract class XMakeDefaultRunner : AsyncProgramRunner<RunnerSettings>() {

    override fun execute(environment: ExecutionEnvironment, state: RunProfileState): Promise<RunContentDescriptor?> {
        return resolvedPromise(doExecute(state, environment))
    }
    protected open fun doExecute(state: RunProfileState, environment: ExecutionEnvironment) : RunContentDescriptor? {
        return executeState(state, environment, this)
    }
}
