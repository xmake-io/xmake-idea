/*!A Xmake integration in IntelliJ IDEA/Clion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2015-present, Xmake Open Source Community.
 *
 * @author      ruki
 * @file        XMakeInfoManager.kt
 *
 */
package io.xmake.utils.info

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import io.xmake.file.highlight.XMakeLuaLexer
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

    fun probeXMakeApis(toolkit: Toolkit?) {
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

                val apisString = runXMakeShow("apis")

                with(xmakeInfo) {
                    apis = parseApis(apisString)
                }

                if (xmakeInfo.apis.isNotEmpty()) {
                    XMakeLuaLexer.updateApis(xmakeInfo.apis)
                }
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
