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
 * @file        TargetManager.kt
 *
 */
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