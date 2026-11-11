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

package io.xmake.debug.clion.compdb

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.jetbrains.cidr.cpp.compdb.CompDBUtil
import io.xmake.project.XMakeSettings
import io.xmake.run.command.compileCommandsFile
import java.nio.file.Files

internal fun autoLinkCompilationDatabase(project: Project) {
    if (project.isDisposed) return
    if (!XMakeSettings.getInstance(project).state.autoLinkCompilationDatabase) return
    if (CompDBUtil.linkedProjectSettings(project).isNotEmpty()) return
    val file = compileCommandsFile(project)?.takeIf { Files.isRegularFile(it) } ?: return
    val application = ApplicationManager.getApplication()
    val link = {
        if (!project.isDisposed && CompDBUtil.linkedProjectSettings(project).isEmpty()) {
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
                ?.let { CompDBUtil.linkCompDBProject(project, it) }
        }
    }
    if (application.isDispatchThread) link() else application.invokeLater(link)
}
