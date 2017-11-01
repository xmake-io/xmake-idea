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
        commandLine: GeneralCommandLine
) : CommandLineState(environment) {

    // the command line
    val commandLine = commandLine

    // start process
    override fun startProcess(): ProcessHandler {

        // make handler
        val handler = KillableColoredProcessHandler(commandLine)

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
