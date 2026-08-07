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
 * @file        CleanConfigurationAction.kt
 *
 */
package io.xmake.actions

import com.intellij.openapi.project.Project
import io.xmake.project.console.XMakeConsole
import io.xmake.run.command.XMakeCommandFactory
import io.xmake.run.command.XMakeConsoleOptions
import io.xmake.run.command.xmakeExecutionService

class CleanConfigurationAction : XMakeCommandAction() {

    override suspend fun execute(
        project: Project,
        console: XMakeConsole,
        commands: XMakeCommandFactory,
    ) {
        project.xmakeExecutionService.execute(
            console,
            commands.createCleanConfiguration(),
            XMakeConsoleOptions(showExitCode = true),
        )
    }
}
