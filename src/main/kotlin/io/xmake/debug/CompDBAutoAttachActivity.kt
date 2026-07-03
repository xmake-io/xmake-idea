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
 * @file        CompDBAutoAttachActivity.kt
 *
 */
package io.xmake.debug

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.xmake.utils.SystemUtils

/**
 * On project open, if this is an xmake project and a `compile_commands.json` already exists,
 * attach it to CLion's Compilation Database so IntelliSense works without a manual
 * "Update Compile Commands". Only attaches an existing file — it does not generate one on
 * startup (that needs a selected run configuration). No-ops on IDEA Community, where the
 * reflective attach in [CompDBSupport.refreshIntelliSense] does nothing.
 */
class CompDBAutoAttachActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (!SystemUtils.isXMakeProject(project)) return
        val file = CompDBSupport.compileCommandsFile(project) ?: return
        if (!file.isFile) return
        CompDBSupport.refreshIntelliSense(project)
    }
}
