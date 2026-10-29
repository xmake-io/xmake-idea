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
 * @file        XMakeDirectoryProjectConfigurator.kt
 *
 */
package io.xmake.project.wizard

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectConfigurator
import com.intellij.project.stateStore
import io.xmake.project.directory.configureInitialXMakeProjectLayout
import io.xmake.project.directory.hasRootXMakeLua
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Seeds the XMake layout when a folder with a root xmake.lua is opened: the same initial
 * configuration the wizard applies after `xmake create`. Wizard-created projects skip this
 * configurator — their setupProject calls the shared initializer itself, with the wizard's
 * fuller context (SSH host directories, run configuration).
 *
 * This only persists the initial state at open time; discovering an xmake project that was
 * already open stays with the lazy resolution chain (root xmake.lua detection and the
 * project-directory resolution service), which reacts to file and configuration changes.
 */
class XMakeDirectoryProjectConfigurator : DirectoryProjectConfigurator.AsyncDirectoryProjectConfigurator() {

    override suspend fun configure(
        project: Project,
        baseDir: VirtualFile,
        moduleRef: Ref<Module>,
        isProjectCreatedWithWizard: Boolean,
    ) {
        if (isProjectCreatedWithWizard) return
        val baseDirPath = baseDir.toNioPath()
        if (!hasRootXMakeLua(baseDirPath.toString())) return
        withContext(Dispatchers.EDT) {
            if (project.isDisposed) return@withContext
            val storeDirectory = project.stateStore.directoryStorePath ?: return@withContext
            val module = moduleRef.get()
                ?: ModuleManager.getInstance(project).modules.firstOrNull()
                ?: runWriteAction {
                    ModuleManager.getInstance(project).newModule(
                        storeDirectory.resolve(project.name),
                        ModuleTypeManager.getInstance().getDefaultModuleType().id,
                    )
                }.also(moduleRef::set)
            configureInitialXMakeProjectLayout(project, baseDirPath, module)
        }
    }
}
