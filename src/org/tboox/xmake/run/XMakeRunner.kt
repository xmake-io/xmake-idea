package org.tboox.xmake.run;

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.runners.DefaultProgramRunner
import com.intellij.openapi.diagnostic.Logger

class XMakeRunner : DefaultProgramRunner() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        Log.info("canRun")
        //return executorId == DefaultRunExecutor.EXECUTOR_ID && profile is XMakeCommandConfiguration
        return true
    }

    override fun getRunnerId(): String = "XMakeRunner"

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunner::class.java.getName())
    }
}
