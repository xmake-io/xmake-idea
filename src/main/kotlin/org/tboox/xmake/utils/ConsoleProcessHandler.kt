package org.tboox.xmake.utils

import com.intellij.execution.process.*
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.execution.configurations.GeneralCommandLine
import java.io.IOException
import java.io.OutputStreamWriter

class ConsoleProcessHandler(
    private val consoleView: ConsoleView,
    commandLine: GeneralCommandLine,
    showExitCode: Boolean = false
) : KillableColoredProcessHandler(commandLine) {

    // the output content
    var outputContent = ""

    // initialize
    init {

        // shows exit code upon termination
        if (showExitCode) {
            ProcessTerminatedListener.attach(this)
        }
    }

    override fun coloredTextAvailable(textOriginal: String, attributes: Key<*>) {
        append(textOriginal, attributes)
    }

    // append info to the console view
    private fun append(s: String, k: Key<*>) {
        this.consoleView.print(s, ConsoleViewContentType.getConsoleViewType(k))
        outputContent += s
    }

    // append input info to the console view
    fun input(s: String) {
        try {
            processInputWriter.append(s)
        } catch (ex: IOException) {
            Log.error(ex)
        }
    }

    // append input info and flush it
    fun inputWithFlush(s: String) {
        try {
            processInputWriter.append(s)
            processInputWriter.flush()
        } catch (ex: IOException) {
            Log.error(ex)
        }
    }

    var _outputStreamWriter: OutputStreamWriter? = null
    private val processInputWriter: OutputStreamWriter
        get() {
            if (_outputStreamWriter == null) {
                _outputStreamWriter = OutputStreamWriter(processInput)
            }
            return _outputStreamWriter ?: OutputStreamWriter(processInput)
        }

    // flush io
    fun flush() {
        try {
            processInputWriter.flush()
        } catch (ex: IOException) {
            Log.error(ex)
        }
    }

    companion object {
        private val Log = Logger.getInstance(ConsoleProcessHandler::class.java.getName())
    }
}
