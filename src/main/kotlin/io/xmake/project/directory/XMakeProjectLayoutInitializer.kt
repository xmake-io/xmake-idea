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
 * @file        XMakeProjectLayoutInitializer.kt
 *
 */
package io.xmake.project.directory

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.project.stateStore
import java.nio.file.Path

/**
 * Applies the initial XMake layout of one project directory at most once, and persists it
 * as the project directory when none is configured yet (first-wins, so a later manual
 * configuration is never overwritten).
 *
 * The wizard calls this from its setupProject once `xmake create` succeeded, and the directory
 * configurator calls it when an existing folder with a root xmake.lua is opened; both entry
 * points converge here.
 */
fun configureInitialXMakeProjectLayout(project: Project, baseDir: Path, module: Module) {
    val baseUrl = VfsUtil.pathToUrl(baseDir.toString())
    ModuleRootModificationUtil.updateModel(module) { model ->
        if (model.contentEntries.any { it.url == baseUrl }) return@updateModel
        with(model.addContentEntry(baseUrl)) {
            addSourceFolder(VfsUtil.fileToUrl(baseDir.resolve("src").toFile()), false)
            project.stateStore.directoryStorePath
                ?.let { addExcludeFolder(VfsUtil.fileToUrl(it.toFile())) }
            addExcludeFolder(VfsUtil.fileToUrl(baseDir.resolve(".xmake").toFile()))
            addExcludeFolder(VfsUtil.fileToUrl(baseDir.resolve("build").toFile()))
        }
    }
    val directories = project.xmakeProjectDirectories
    val current = directories.state
    if (current.localDirectory.isBlank() && current.hostDirectories.isEmpty()) {
        directories.replaceState(XMakeProjectDirectoryState(localDirectory = baseDir.toString()))
    }
}
