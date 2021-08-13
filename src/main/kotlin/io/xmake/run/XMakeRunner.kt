package io.xmake.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.DefaultProgramRunner
import com.intellij.openapi.diagnostic.Logger

class XMakeRunner : DefaultProgramRunner() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        return executorId == DefaultRunExecutor.EXECUTOR_ID && profile is XMakeRunConfiguration
    }

    override fun getRunnerId(): String = "XMakeRunner"

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunner::class.java.getName())
    }
}
