package org.tboox.xmake.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.tboox.xmake.project.xmakeConsoleView
import org.tboox.xmake.project.xmakeOutputPanel
import org.tboox.xmake.project.xmakeProblemList
import org.tboox.xmake.project.xmakeToolWindow
import org.tboox.xmake.shared.XMakeProblem
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.File
import java.util.regex.Pattern

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

    // parse problems for the given line
    private fun parseProblem(info: String): XMakeProblem? {

        val pattern = Pattern.compile("^(error: )?(.*?):([0-9]*):([0-9]*): (.*?): (.*)\$")
        val matcher = pattern.matcher(info)
        if (matcher.find()) {
            val file    = matcher.group(2)
            val line    = matcher.group(3)
            val column  = matcher.group(4)
            val kind    = matcher.group(5)
            val message = matcher.group(6)
            return XMakeProblem(file, line, column, kind, message)
        }
        return null
    }

    // run process in console
    fun runvInConsole(project: Project, commandLine: GeneralCommandLine, showConsole: Boolean = true, showProblem: Boolean = false): ProcessHandler {

        // create handler
        val handler = ConsoleProcessHandler(project.xmakeConsoleView, commandLine)

        // show console?
        if (showConsole) {

            // show tool window first
            project.xmakeToolWindow.show {
                project.xmakeOutputPanel.showPanel()
            }
        }

        // show problem?
        if (showProblem) {
            handler.addProcessListener(object : ProcessAdapter() {

                override fun processTerminated(e: ProcessEvent) {
                    val content = handler.outputContent
                    ApplicationManager.getApplication().invokeLater {
                        val problems = mutableListOf<XMakeProblem>()
                        content.split('\n').forEach {
                            val problem = parseProblem(it.trim())
                            if (problem !== null) {
                                problems.add(problem)
                            }
                        }
                        project.xmakeProblemList = problems
                    }
                }
            })
        }
    //    project.xmakeProblemList = listOf("xxxxxxxx2") // listOf(s)

        // start process
        handler.startNotify()

        // failed
        return handler
    }
}
