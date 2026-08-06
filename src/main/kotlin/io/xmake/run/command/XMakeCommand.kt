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
package io.xmake.run.command

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.Toolkit
import io.xmake.utils.execute.createProcess

/**
 * A prepared XMake invocation together with the host context that produced it.
 *
 * Keeping the toolkit and resolved working directory beside the command line lets
 * later execution and file-sync stages reuse the same context without consulting
 * mutable project or run-manager state.
 */
internal class XMakeCommand(
    val commandLine: GeneralCommandLine,
    val toolkit: Toolkit,
    val workingDirectory: String,
) {
    fun createProcess(project: Project): Process =
        commandLine.createProcess(toolkit, project, workingDirectory)
}
