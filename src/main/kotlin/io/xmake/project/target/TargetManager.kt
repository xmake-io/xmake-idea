package io.xmake.project.target

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.Toolkit
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.probeXmakeTargetCommand
import io.xmake.utils.execute.runProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

@Service(Service.Level.PROJECT)
class TargetManager(
    private val project: Project,
    private val scope: CoroutineScope,
) {

    fun detectXMakeTarget(toolkit: Toolkit, workingDirectory: String): List<String> = runBlocking(Dispatchers.IO) {
        val process = probeXmakeTargetCommand
            .withExePath(toolkit.path)
            .withWorkDirectory(workingDirectory)
            .also { Log.debug(it.commandLineString) }
            .createProcess(toolkit)
        val (stdout, exitCode) = runProcess(process)
        Log.debug("ExitCode: $exitCode Output: $stdout")
        val targets = stdout.getOrElse { "" }.trimEnd().split(Regex("\\r\\n|\\n|\\r"))
        Log.debug("Targets: $targets")
        return@runBlocking listOf("default", "all") + targets
    }

    companion object {
        fun getInstance(project: Project): TargetManager = project.serviceOrNull() ?: throw IllegalStateException()
        private val Log = logger<TargetManager>()
    }
}