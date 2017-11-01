package org.tboox.xmake.run

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger

class XMakeRunState(
        environment: ExecutionEnvironment,
        runTarget: String,
        workingDirectory: String,
        environmentVariables: EnvironmentVariablesData,
        verboseOutput: Boolean
) : CommandLineState(environment) {

    // the run target
    val runTarget = runTarget

    // the working Directory
    val workingDirectory = workingDirectory

    // the enviroment variables
    val environmentVariables = environmentVariables

    // the verbose output
    val verboseOutput = verboseOutput

    override fun startProcess(): ProcessHandler {

        // init parameters
        val parameters = mutableListOf("run")
        if (verboseOutput) {
            parameters.add("-v")
        }
        if (runTarget == "all") {
            parameters.add("-a")
        } else if (runTarget != "" && runTarget != "default") {
            parameters.add(runTarget)
        }

        // make command
        val cmd = GeneralCommandLine("xmake")
                .withParameters(parameters)
                .withCharset(Charsets.UTF_8)
                .withWorkDirectory(workingDirectory)
                .withEnvironment(environmentVariables.envs)
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
