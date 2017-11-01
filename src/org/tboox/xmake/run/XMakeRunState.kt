package org.tboox.xmake.run

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import org.tboox.xmake.utils.SystemUtils

class XMakeRunState(
        environment: ExecutionEnvironment,
        runParameters: List<String>,
        workingDirectory: String,
        environmentVariables: EnvironmentVariablesData
) : CommandLineState(environment) {

    // the run parameters
    val runParameters = runParameters

    // the working Directory
    val workingDirectory = workingDirectory

    // the enviroment variables
    val environmentVariables = environmentVariables

    override fun startProcess(): ProcessHandler {

        // make command
        val cmd = GeneralCommandLine(SystemUtils.xmakeProgram)
                .withParameters(runParameters)
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
