package io.xmake.utils.info

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import io.xmake.project.toolkit.Toolkit
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.runProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

@Service(Service.Level.PROJECT)
class XMakeInfoManager(val project: Project, private val scope: CoroutineScope) {

    val xmakeInfo: XMakeInfo = XMakeInfo()

    // Todo
    val cachedXMakeInfoMap: MutableMap<Toolkit, XMakeInfo> = mutableMapOf()

    fun probeXMakeInfo(toolkit: Toolkit?) {
        scope.launch {
            toolkit?.let {
                val workingDirectory = project.basePath?.let { path -> File(path) }

                suspend fun runXMakeShow(key: String): String {
                    val cmd = GeneralCommandLine(
                        "xmake show -l $key --json".split(" ")
                    ).apply {
                        workingDirectory?.let { wd -> withWorkDirectory(wd) }
                        withEnvironment("XMAKE_SKIP_HISTORY", "1")
                        withEnvironment("XMAKE_ROOT", "y")
                        withEnvironment("XMAKE_COLOR_TERM", "nocolor")
                    }
                    val result = runProcess(cmd.createProcess(it)).first.getOrDefault("")
                    return result
                }

                val architecturesString = runXMakeShow("architectures")
                val buildModesString = runXMakeShow("buildmodes")
                val platformsString = runXMakeShow("platforms")
                val targetsString = runXMakeShow("targets")
                val toolchainsString = runXMakeShow("toolchains")

                with(xmakeInfo) {
                    architectures = parseArchitectures(architecturesString)
                    buildModes = parseBuildModes(buildModesString)
                    platforms = parsePlatforms(platformsString)
                    targets = parseTargets(targetsString)
                    toolchains = parseToolchains(toolchainsString)
                }

                project.messageBus.syncPublisher(XMAKE_INFO_TOPIC).onXMakeInfoUpdated(xmakeInfo)
            }
        }
    }

    interface XMakeInfoListener {
        fun onXMakeInfoUpdated(xmakeInfo: XMakeInfo)
    }

    companion object {
        val Log = logger<XMakeInfoManager>()
        val XMAKE_INFO_TOPIC = Topic.create("XMake Info Updated", XMakeInfoListener::class.java)

        fun getInstance(project: Project): XMakeInfoManager = project.serviceOrNull() ?: throw IllegalStateException()
    }
}
