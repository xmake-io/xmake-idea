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
package io.xmake.utils.extension

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.utils.execute.SyncDirection

/**
 * Runtime capabilities supplied by a host extension.
 *
 * This is intentionally an aggregate staging interface: callers still consume one extension
 * implementation today. Additional host kinds can expose these capabilities separately when
 * host providers and execution are split for additional backends.
 */
interface ToolkitHostCapabilities {
    suspend fun syncProject(
        project: Project,
        host: ToolkitHost,
        direction: SyncDirection,
        hostDirectory: String,
    )

    suspend fun resolveDefaultWorkingDirectory(project: Project, host: ToolkitHost): String? = null

    fun startProcess(host: ToolkitHost, command: GeneralCommandLine): Process
}
