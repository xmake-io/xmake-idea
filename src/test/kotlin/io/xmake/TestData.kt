package io.xmake

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

object TestData {
    // get from local xmake
    val xmakeVersion: String = testIORunv(listOf("xmake", "--version"))
    val xmakeFHelp: String= testIORunv(listOf("xmake", "f", "-h"))

    private fun testIORunv(argv: List<String>, workDir: String? = null): String {
        var result = ""
        var bufferReader: BufferedReader? = null
        try {
            val processBuilder = ProcessBuilder(argv)
            if (workDir !== null) {
                processBuilder.directory(File(workDir))
            }
            processBuilder.environment().put("COLORTERM", "nocolor")
            val process = processBuilder.start()
            bufferReader = BufferedReader(InputStreamReader(process.getInputStream()))
            var line: String? = bufferReader.readLine()
            while (line != null) {
                result += line + "\n"
                line = bufferReader.readLine()
            }
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
        return result
    }
}