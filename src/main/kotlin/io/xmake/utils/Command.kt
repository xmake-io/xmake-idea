package io.xmake.utils

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.utils.exception.XMakeToolkitNotSetException
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.runProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
        .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
    val project = ProjectManager.getInstance().defaultProject
    try {
        val activatedToolkit = project.activatedToolkit ?: throw XMakeToolkitNotSetException()
        val (result, exitCode) = runBlocking(Dispatchers.Default) {
            runProcess(commandLine.createProcess(activatedToolkit))
        }
        return result.getOrDefault("").split(Regex("\\s+"))
    } catch (e: XMakeToolkitNotSetException) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("XMake")
            .createNotification("Error with XMake Toolkit", e.message ?: "", NotificationType.ERROR)
            .notify(project)
        return emptyList()
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
