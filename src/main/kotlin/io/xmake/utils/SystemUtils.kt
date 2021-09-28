package io.xmake.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.xmake.project.xmakeConsoleView
import io.xmake.project.xmakeOutputPanel
import io.xmake.project.xmakeProblemList
import io.xmake.project.xmakeToolWindow
import io.xmake.shared.XMakeProblem
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern


object SystemUtils {

    // the log
    private val LOG = logger<SystemUtils>()

    // the xmake program
    private var _xmakeProgram: String = ""
    var xmakeProgram: String
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
            val programs = arrayOf(
                "xmake",
                (System.getenv("HOME") ?: "") + "/.local/bin/xmake",
                "/usr/local/bin/xmake",
                "/usr/bin/xmake"
            )
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
                        LOG.error(e)
                        e.printStackTrace()
                    }
                }
            }

            // ok?
            return _xmakeProgram
        }
        set(value) {
            _xmakeProgram = value
        }

    // the xmake version
    private var _xmakeVersion: String = ""
    var xmakeVersion: String
        get() {
            if (_xmakeVersion == "") {
                val result = ioRunv(listOf(xmakeProgram, "--version")).split(',')
                if (result.size > 0) {
                    _xmakeVersion = result[0]
                }
            }
            return _xmakeVersion
        }
        set(value) {
            _xmakeVersion = value
        }

    // get platform
    fun platform(): String = when {
        SystemInfo.isWindows -> "windows"
        SystemInfo.isMac -> "macosx"
        else -> "linux"
    }

    // run command with arguments
    fun Runv(argv: List<String>, workingDirectory: String? = null): Int {

        var code = -1
        try {

            // init process builder
            val processBuilder = ProcessBuilder(argv)

            // init working directory
            if (workingDirectory !== null) {
                processBuilder.directory(File(workingDirectory))
            }

            // run process
            val process = processBuilder.start()

            // wait for process
            code = process.waitFor()

        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
        }

        // ok?
        return code
    }

    // run command with arguments and return output
    fun ioRunv(argv: List<String>, workingDirectory: String? = null): String {

        var result = ""
        var bufferReader: BufferedReader? = null
        try {

            // init process builder
            val processBuilder = ProcessBuilder(argv)

            // init working directory
            if (workingDirectory !== null) {
                processBuilder.directory(File(workingDirectory))
            }

            // disable color for xmake
            processBuilder.environment().put("COLORTERM", "nocolor")

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

        if (SystemInfo.isWindows) {

            // gbk => utf8
            val info_utf8 = String(info.toByteArray(), charset("UTF-8"))

            // parse problem info
            val pattern = Pattern.compile("(.*?)\\(([0-9]*)\\): (.*?) .*?: (.*)")
            val matcher = pattern.matcher(info_utf8)
            if (matcher.find()) {
                val file = matcher.group(1)
                val line = matcher.group(2)
                val kind = matcher.group(3)
                val message = matcher.group(4)
                return XMakeProblem(file, line, "0", kind, message)
            }

        } else {

            // parse problem info
            val pattern = Pattern.compile("^(error: )?(.*?):([0-9]*):([0-9]*): (.*?): (.*)\$")
            val matcher = pattern.matcher(info)
            if (matcher.find()) {
                val file = matcher.group(2)
                val line = matcher.group(3)
                val column = matcher.group(4)
                val kind = matcher.group(5)
                val message = matcher.group(6)
                return XMakeProblem(file, line, column, kind, message)
            }
        }
        return null
    }

    // run process in console
    fun runvInConsole(
        project: Project,
        commandLine: GeneralCommandLine,
        showConsole: Boolean = true,
        showProblem: Boolean = false,
        showExitCode: Boolean = false
    ): ProcessHandler {

        // create handler
        val handler = ConsoleProcessHandler(project.xmakeConsoleView, commandLine, showExitCode)

        // show console?
        if (showConsole) {

            // show tool window first
            project.xmakeToolWindow?.show {
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

        // start process
        handler.startNotify()

        // failed
        return handler
    }
}

val VirtualFile.pathAsPath: Path get() = Paths.get(path)