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
 * @file        ToolkitHostExtension.kt
 *
 */
package io.xmake.utils.extension

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.utils.execute.SyncDirection
import java.awt.event.ActionListener

interface ToolkitHostExtension {
    val KEY: String

    fun getHostType(): String

    fun getToolkitHosts(project: Project? = null): List<ToolkitHost>

    fun filterRegistered(): (Toolkit) -> Boolean

    fun createToolkit(host: ToolkitHost, path: String, version: String): Toolkit

    suspend fun syncProject(
        project: Project,
        host: ToolkitHost,
        direction: SyncDirection,
        remoteDirectory: String,
    )

    suspend fun ToolkitHost.loadHostBackend(project: Project? = null)

    fun DirectoryBrowser.createBrowseListener(host: ToolkitHost): ActionListener

    fun GeneralCommandLine.createProcess(host: ToolkitHost): Process
}
