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
import com.intellij.execution.ExecutionTargetProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import io.xmake.project.profile.xmakeBuildProfiles
import io.xmake.run.XMakeRunConfiguration

class XMakeBuildProfileExecutionTargetProvider : ExecutionTargetProvider() {
    override fun getTargets(project: Project, configuration: RunConfiguration): List<ExecutionTarget> {
        if (configuration !is XMakeRunConfiguration) return emptyList()
        val preferredProfileId = configuration.preferredBuildProfileId
        return project.xmakeBuildProfiles.profiles
            .sortedBy { profile -> profile.id != preferredProfileId }
            .map { profile ->
                XMakeBuildProfileExecutionTarget(project, profile)
            }
    }
}
