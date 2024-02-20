package io.xmake.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.ApplicationManager
import com.jetbrains.rd.util.Runnable
import io.xmake.utils.interact.kSysEnv
import io.xmake.utils.interact.kLineSep
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * [ioRunv]
 *
 * don't use it easily, it will block the main thread
 *
 * @param argv the command arguments
 * @param workDir the working directory
 * @return a List<String>, the all output of the command
 *
 * error: return empty list -> emptyList()
 */
fun ioRunv(argv: List<String>, workDir: String? = null): List<String> {
    val commandLine: GeneralCommandLine = GeneralCommandLine(argv)
        .withWorkDirectory(workDir)
        .withCharset(Charsets.UTF_8)
        .withEnvironment(kSysEnv)
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    commandLine.withEnvironment("COLORTERM", "nocolor")
    try {
        val output = ExecUtil.execAndGetOutput(commandLine)
        return output.stdout.split(kLineSep)
    } catch (e: ProcessNotCreatedException) {
        return emptyList()
    }
}

fun ioRunvInPool(argv: List<String>, workDir: String? = null): List<String> {
    val callable: Callable<List<String>> = Callable {
        return@Callable ioRunv(argv, workDir)
    }
    return ApplicationManager.getApplication().executeOnPooledThread(callable).get()
}

/**
 * [ioRunvBackground]
 *
 * processing in background
 * @param argv the command arguments
 * @param workDir the working directory
 * @return Unit (void)
 *
 * error: pop up prompt in the lower right corner if the operation fails
 */
fun ioRunvBackground(argv: List<String>, workDir: String? = null): Unit {
    TODO()
}

/**
 * [ioRunvNonBlock]
 *
 * will not block the main thread, will poll for return
 *
 * @param argv the command arguments
 * @param workDir the working directory
 * @return a List<String>, the all output of the command
 *
 * error: return empty list -> emptyList()
 */
fun ioRunvNonBlock(argv: List<String>, workDir: String? = null): List<String>{
    TODO()
}

fun ioRunvSingle(argv: List<String>, workDir: String? = null): List<String> {
    val call = Callable {
        return@Callable ioRunv(argv, workDir)
    }

    val executor = Executors.newSingleThreadExecutor()
    val commandOutput: Future<List<String>> = executor.submit(call)
    val result = commandOutput.get()
    executor.shutdown()
    return result
}

/**
 * [vRunv]
 *
 * output on console
 * @param console the console
 * @param argv the command arguments
 * @param workDir the working directory
 * @return void
 */
inline fun vRunv(console: String/*TODO()*/, argv: List<String>, workDir: String? = null) {
    TODO()
}
