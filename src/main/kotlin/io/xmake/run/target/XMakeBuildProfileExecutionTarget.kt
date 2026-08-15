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
package io.xmake.run.target

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.xmakeBuildProfiles
import io.xmake.run.XMakeRunConfiguration
import javax.swing.Icon

internal class XMakeBuildProfileExecutionTarget(
    private val project: Project,
    profile: XMakeBuildProfile,
) : ExecutionTarget() {
    val profileId: String = profile.id
    private val fallbackDisplayName = profile.name

    override fun getId(): String = "xmake-build-profile:$profileId"

    override fun getDisplayName(): String =
        resolveProfile()?.name ?: fallbackDisplayName

    override fun getGroupName(): String = "XMake Profiles"

    override fun getIcon(): Icon? = null

    override fun canRun(configuration: RunConfiguration): Boolean = configuration is XMakeRunConfiguration

    override fun isReady(): Boolean {
        return resolveProfile()?.canExecute(project) == true
    }

    private fun resolveProfile(): XMakeBuildProfile? =
        project.takeUnless(Project::isDisposed)
            ?.xmakeBuildProfiles
            ?.findProfile(profileId)
}
