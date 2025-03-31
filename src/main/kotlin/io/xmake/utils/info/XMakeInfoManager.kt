package io.xmake.utils.info

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.Toolkit
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.runProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class XMakeInfoManager(val project: Project, private val scope: CoroutineScope) {

    val xmakeInfo: XMakeInfo = XMakeInfo()

    // Todo
    val cachedXMakeInfoMap: MutableMap<Toolkit, XMakeInfo> = mutableMapOf()

    fun probeXMakeInfo(toolkit: Toolkit?) {
        scope.launch {
            toolkit?.let {
                val apisString = runProcess(
                    GeneralCommandLine(
                        "xmake show -l apis --json".split(" ")
                    ).createProcess(it)
                ).first.getOrDefault("")

                val architecturesString = runProcess(
                    GeneralCommandLine(
                        "xmake show -l architectures --json".split(" ")
                    ).createProcess(it)
                ).first.getOrDefault("")

                val buildModesString = runProcess(
                    GeneralCommandLine(
                        "xmake show -l buildmodes --json".split(" ")
                    ).createProcess(it)
                ).first.getOrDefault("")

                val envsString = runProcess(
                    GeneralCommandLine(
                        "xmake show -l envs --json".split(" ")
                    ).createProcess(it)
                ).first.getOrDefault("")

                val packagesString = runProcess(
                    GeneralCommandLine(
                        "xmake show -l packages --json".split(" ")
                    ).createProcess(it)
                ).first.getOrDefault("")

                val platformsString = runProcess(
                    GeneralCommandLine(
                        "xmake show -l platforms --json".split(" ")
                    ).createProcess(it)
                ).first.getOrDefault("")

                val policiesString = runProcess(
                    GeneralCommandLine(
                        "xmake show -l policies --json".split(" ")
                    ).createProcess(it)
                ).first.getOrDefault("")

                val rulesString = runProcess(
                    GeneralCommandLine(
                        "xmake show -l rules --json".split(" ")
                    ).createProcess(it)
                ).first.getOrDefault("")

                val targetsString = runProcess(
                    GeneralCommandLine(
                        "xmake show -l targets --json".split(" ")
                    ).createProcess(it)
                ).first.getOrDefault("")

                val toolchainsString = runProcess(
                    GeneralCommandLine(
                        "xmake show -l toolchains --json".split(" ")
                    ).createProcess(it)
                ).first.getOrDefault("")

                with(xmakeInfo) {
                    apis = parseApis(apisString)
                    architectures = parseArchitectures(architecturesString)
                    buildModes = parseBuildModes(buildModesString)
                    platforms = parsePlatforms(platformsString)
                    policies = parsePolicies(policiesString)
                    rules = parseRules(rulesString)
//                    targets = parseTargets(targetsString)
                    toolchains = parseToolchains(toolchainsString)

                    Log.info(
                        "XMake Info: " +
                                "$apis, " +
                                "$architectures, " +
                                "$platforms, " +
                                "$policies, " +
                                "$rules, " +
                                "$targets, " +
                                "$toolchains"
                    )
                }

                println(xmakeInfo)

            }
        }
    }

    companion object {
        val Log = logger<XMakeInfoManager>()
        fun getInstance(project: Project): XMakeInfoManager = project.serviceOrNull() ?: throw IllegalStateException()
    }
}