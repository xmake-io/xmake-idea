package org.tboox.xmake.utils

import com.intellij.execution.process.*
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.execution.configurations.GeneralCommandLine

import java.io.IOException
import java.io.OutputStreamWriter

class ConsoleProcessHandler(private val myConsoleView: ConsoleView, commandLine: GeneralCommandLine) : OSProcessHandler(commandLine) {


    init {

        this.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent?, k: Key<*>?) {
                append(event!!.text, k)
            }
        })
        ProcessTerminatedListener.attach(this)
    }

    private fun append(s: String, k: Key<*>?) {
        /*
        if (ProcessOutputTypes.STDERR == k) {
            this.myConsoleView.print(s, ConsoleViewContentType.ERROR_OUTPUT)
        } else if (ProcessOutputTypes.SYSTEM == k) {
            this.myConsoleView.print(s, ConsoleViewContentType.SYSTEM_OUTPUT)
        } else if (ProcessOutputTypes.STDOUT == k) {
            this.myConsoleView.print(s, ConsoleViewContentType.NORMAL_OUTPUT)
        }*/

        this.myConsoleView.print(s, ConsoleViewContentType.NORMAL_OUTPUT)

    }

    fun input(s: String) {
        try {
            processInputWriter.append(s)
        } catch (ex: IOException) {
            Log.error(ex)
        }
    }

    fun inputWithFlush(s: String) {
        try {
            processInputWriter.append(s)
            processInputWriter.flush()
        } catch (ex: IOException) {
            Log.error(ex)
        }
    }

    var _myOutputStreamWriter: OutputStreamWriter? = null
    private val processInputWriter: OutputStreamWriter
        get() {
            if (_myOutputStreamWriter == null) {
                _myOutputStreamWriter = OutputStreamWriter(processInput)
            }
            return _myOutputStreamWriter ?: OutputStreamWriter(processInput)
        }

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
