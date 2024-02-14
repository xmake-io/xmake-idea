package io.xmake.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.execution.util.ExecUtil
import com.jetbrains.rd.util.Callable
import io.xmake.utils.interact.kSystemEnv
import io.xmake.utils.interact.kLineSeparator
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * [ioRunv]
 *
 * @param argv the command arguments
 * @param workDir the working directory
 * @return a List<String>, the all output of the command
 */
fun ioRunv(argv: List<String>, workDir: String? = null): List<String> {
    val call = Callable {
        val ret: List<String> = emptyList()
        try {
            val commandLine: GeneralCommandLine = GeneralCommandLine(argv)
                .withWorkDirectory(workDir)
                .withCharset(Charsets.UTF_8)
                .withEnvironment(kSystemEnv)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            commandLine.withEnvironment("COLORTERM", "nocolor")
            val output = ExecUtil.execAndGetOutput(commandLine)
            output.stdout.split(kLineSeparator)
        } catch (e: Exception) {
            e.printStackTrace()
            ret
        }
    }

    val executor = Executors.newSingleThreadExecutor()
    val commandOutput: Future<List<String>> = executor.submit(call)
    val result = commandOutput.get()
    executor.shutdown()
    return result
}


/**
 * [runVOutLine]
 *
 * @param argv the command arguments
 * @param minLine return begin line
 * @param maxLine return end line
 * @param workDir the working directory
 * @return a string containing the lines of output from the command,
 * starting from the line number specified by `minLine` and ending at the line number specified by `maxLine`.
 * If the command produces fewer lines than `minLine`, the return will be an empty string.
 * If the command produces lines but fewer than `maxLine`, all lines from `minLine` to the end of output will be returned.
 * Lines are returned as a single string, with each line separated by the system's line separator.
 */

fun ioRunvOutLine(argv: List<String>, minLine: Int, maxLine: Int = minLine, workDir: String? = null): String {
    TODO()
}

/**
 * [vRunV]
 *
 * output on console
 * @param console the console
 * @param argv the command arguments
 * @param workDir the working directory
 * @return void
 */
inline fun vRunV(console: String/*TODO()*/, argv: List<String>, workDir: String? = null) {
    TODO()
}