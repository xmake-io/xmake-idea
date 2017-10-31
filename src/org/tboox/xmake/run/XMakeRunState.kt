package org.tboox.xmake.run

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger

class XMakeRunState(
        environment: ExecutionEnvironment,
        target: String
) : CommandLineState(environment) {

    // the run target
    val runTarget = target

    override fun startProcess(): ProcessHandler {

        // init parameters
        val parameters = mutableListOf("run")
        if (runTarget == "all") {
            parameters.add("-a")
        } else if (runTarget != "" && runTarget != "default") {
            parameters.add(runTarget)
        }

        // get project directory
        val projectdir = getEnvironment().getProject().basePath

        // make command
        val cmd = GeneralCommandLine("xmake")
                .withParameters(parameters)
                .withCharset(Charsets.UTF_8)
                .withWorkDirectory(projectdir)
                .withRedirectErrorStream(true)

        // make handler
        val handler = KillableColoredProcessHandler(cmd)

        // shows exit code upon termination
        ProcessTerminatedListener.attach(handler)

        // start this command
        return handler
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunState::class.java.getName())
    }
}
