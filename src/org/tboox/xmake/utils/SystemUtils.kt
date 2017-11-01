package org.tboox.xmake.utils

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.File



object SystemUtils {

    // get log
    private val Log = Logger.getInstance(SystemUtils::class.java.getName())

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

            Log.info(workingDirectory)

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
}
