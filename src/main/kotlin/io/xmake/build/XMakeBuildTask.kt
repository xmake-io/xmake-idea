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
 */
package io.xmake.build

import com.intellij.task.ProjectTask
import io.xmake.run.command.XMakeCommand
import io.xmake.run.command.XMAKE_CONFIG_DIRECTORY_ENV

/** A platform build task whose commands already belong to one configuration. */
internal class XMakeBuildTask(
    private val presentableName: String,
    commands: List<XMakeCommand>,
) : ProjectTask {
    val commands: List<XMakeCommand> = commands.toList()
    val workingDirectory: String

    init {
        require(this.commands.isNotEmpty()) { "An XMake build task must contain at least one command" }
        val firstCommand = this.commands.first()
        require(firstCommand.configurationDirectory.isNotBlank()) {
            "An XMake build task must carry an explicit XMake configuration directory"
        }
        require(this.commands.all { command -> command.hasSameExecutionContextAs(firstCommand) }) {
            "All commands in an XMake build task must share one execution context"
        }
        workingDirectory = firstCommand.workingDirectory
    }

    override fun getPresentableName(): String = presentableName
}

private fun XMakeCommand.hasSameExecutionContextAs(other: XMakeCommand): Boolean =
    toolkit === other.toolkit &&
        workingDirectory == other.workingDirectory &&
        configurationDirectory == other.configurationDirectory

private val XMakeCommand.configurationDirectory: String
    get() = commandLine.environment[XMAKE_CONFIG_DIRECTORY_ENV].orEmpty()
