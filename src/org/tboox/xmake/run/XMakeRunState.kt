package org.tboox.xmake.run

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import org.tboox.xmake.utils.ConsoleProcessHandler

class XMakeRunState(
        environment: ExecutionEnvironment,
        consoleView: ConsoleView,
        commandLine: GeneralCommandLine
) : CommandLineState(environment) {

    // the console view
    val consoleView = consoleView

    // the command line
    val commandLine = commandLine

    // start process
    override fun startProcess(): ProcessHandler {
        return ConsoleProcessHandler(consoleView, commandLine)
    }
}
