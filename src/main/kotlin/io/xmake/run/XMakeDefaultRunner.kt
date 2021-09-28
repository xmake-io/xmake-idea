package io.xmake.run

import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.*
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.concurrency.resolvedPromise
import java.util.concurrent.ExecutionException
import kotlin.jvm.Throws

abstract class XMakeDefaultRunner : ProgramRunner<RunnerSettings> {

    @Throws(ExecutionException::class)
    override fun execute(environment: ExecutionEnvironment) {
        val state = environment.state ?: return
        @Suppress("UnstableApiUsage")
        ExecutionManager.getInstance(environment.project).startRunProfile(environment) {
            resolvedPromise(doExecute(state, environment))
        }
    }
    protected open fun doExecute(state: RunProfileState, environment: ExecutionEnvironment) : RunContentDescriptor? {
        return executeState(state, environment, this)
    }
}
