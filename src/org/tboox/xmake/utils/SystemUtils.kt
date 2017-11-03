package org.tboox.xmake.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.tboox.xmake.project.xmakeConsoleView
import org.tboox.xmake.project.xmakeOutputPanel
import org.tboox.xmake.project.xmakeToolWindow
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.File

object SystemUtils {

    // the log
    private val Log = Logger.getInstance(SystemUtils::class.java.getName())

    // the xmake program
    private var _xmakeProgram:String = ""
    var xmakeProgram:String
        get() {

            // cached? return it directly
            if (_xmakeProgram != "") {
                return _xmakeProgram
            }

            // for windows? return xmake directly
            if (SystemInfo.isWindows) {
                _xmakeProgram = "xmake"
                return _xmakeProgram
            }

            // attempt to get xmake program
            val programs = arrayOf("xmake", (System.getenv("HOME") ?: "") + "/.local/bin/xmake", "/usr/local/bin/xmake", "/usr/bin/xmake")
            for (program in programs) {
                if (program == "xmake" || File(program).exists()) {

                    try {

                        // init process builder
                        val processBuilder = ProcessBuilder(listOf(program, "--version"))

                        // run process
                        val process = processBuilder.start()

                        // wait for process
                        if (process.waitFor() == 0) {
                            _xmakeProgram = program
                            break
                        }

                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }

            // ok?
            return _xmakeProgram
        }
        set(value) { _xmakeProgram = value }

    // get platform
    fun platform(): String = when {
        SystemInfo.isWindows -> "windows"
        SystemInfo.isMac -> "macosx"
        else -> "linux"
    }

    // run command with arguments and return output
    fun ioRunv(argv: List<String>, workingDirectory: String): String {

        var result = ""
        var bufferReader: BufferedReader? = null
        try {

            // init process builder
            val processBuilder = ProcessBuilder(argv)

            // init working directory
            processBuilder.directory(File(workingDirectory))

            // run process
            val process = processBuilder.start()

            // get input buffer reader
            bufferReader = BufferedReader(InputStreamReader(process.getInputStream()))

            // get io output
            var line: String? = bufferReader.readLine()
            while (line != null) {
                result += line + "\n"
                line = bufferReader.readLine()
            }

            // wait for process
            if (process.waitFor() != 0)
                result = ""

        } catch (e: IOException) {
            e.printStackTrace()
        } finally {

            if (bufferReader != null) {
                try {
                    bufferReader.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
        }

        // ok?
        return result
    }

    // run process in console
    fun runvInConsole(project: Project, commandLine: GeneralCommandLine, showConsole: Boolean = true): ProcessHandler {

        // create handler
        val handler = ConsoleProcessHandler(project.xmakeConsoleView, commandLine)
        if (showConsole) {

            // show tool window first
            project.xmakeToolWindow.show {

                // show output panel first
                project.xmakeOutputPanel.showPanel()
            }
        }

        // start process
        handler.startNotify()

        // failed
        return handler
    }
}
