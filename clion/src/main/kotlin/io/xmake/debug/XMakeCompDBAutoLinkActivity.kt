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
 * @file        XMakeCompDBAutoLinkActivity.kt
 *
 */
package io.xmake.debug

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.cidr.cpp.compdb.CompDBUtil
import io.xmake.debug.clion.compdb.XMakeCompDBToolchainBindingService
import io.xmake.project.XMakeSettings
import io.xmake.utils.ui.uiEdt
import kotlinx.coroutines.withContext

class XMakeCompDBAutoLinkActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (project.isDisposed) return
        project.service<XMakeCompDBToolchainBindingService>().bindBeforeLink()
        if (!XMakeSettings.getInstance(project).state.autoLinkCompilationDatabase) return

        withContext(uiEdt) {
            if (CompDBUtil.linkedProjectSettings(project).isNotEmpty()) return@withContext
            XMakeSettings.getInstance(project).state.compileCommandsPath.takeIf { it.isNotBlank() }
                ?.let { path ->
                    val file = LocalFileSystem.getInstance().findFileByPath(path)
                    file?.let { CompDBUtil.linkCompDBProject(project, it) }
                }
        }
    }
}
