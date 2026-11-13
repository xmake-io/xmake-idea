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
package io.xmake.project.directory

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.Disposable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import io.xmake.project.profile.xmakeBuildProfiles
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VfsUtilCore
import io.xmake.project.profile.XMakeBuildProfileManager
import io.xmake.utils.path.WorkingDirectoryResolver

@Service(Service.Level.PROJECT)
class XMakeBuildDirectoryExclusions(
    private val project: Project,
) : Disposable {

    init {
        project.messageBus.connect(this).subscribe(
            XMakeBuildProfileManager.TOPIC,
            XMakeBuildProfileManager.Listener {
                ApplicationManager.getApplication().invokeLater(
                    { if (!project.isDisposed) excludeBuildDirectories() },
                    ModalityState.nonModal(),
                )
            },
        )
    }

    override fun dispose() {
    }

    fun excludeBuildDirectories() {
        val urls = buildDirectoryUrls(project)
        if (urls.isEmpty()) return
        for (module in ModuleManager.getInstance(project).modules) {
            ModuleRootModificationUtil.updateModel(module) { model ->
                for (entry in model.contentEntries) {
                    for (url in urls) {
                        if (VfsUtilCore.isEqualOrAncestor(entry.url, url) && url !in entry.excludeFolderUrls) {
                            entry.addExcludeFolder(url)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun buildDirectoryUrls(project: Project): List<String> {
            val projectDirectory = try {
                project.xmakeProjectDirectories.resolveLocalSyncDirectory()
            } catch (_: RuntimeConfigurationError) {
                return emptyList()
            }
            return project.xmakeBuildProfiles.profiles
                .asSequence()
                .map { profile -> profile.buildDirectory.trim().ifEmpty { DEFAULT_BUILD_DIRECTORY } }
                .mapNotNull { directory ->
                    if (File(directory).isAbsolute)
                        resolveDirectorySetting(project, directory)
                    else
                        "$projectDirectory/$directory"
                }
                .distinct()
                .map(VfsUtil::pathToUrl)
                .toList()
        }

        private fun resolveDirectorySetting(project: Project, directory: String): String? = try {
            WorkingDirectoryResolver.resolve(project, directory).takeIf(String::isNotEmpty)
        } catch (_: RuntimeException) {
            null
        }

        private const val DEFAULT_BUILD_DIRECTORY = "build"
    }
}

class XMakeBuildDirectoryExcludeActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        withContext(Dispatchers.EDT) {
            project.service<XMakeBuildDirectoryExclusions>().excludeBuildDirectories()
        }
    }
}
