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

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetManager
import com.intellij.openapi.project.Project
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.xmakeBuildProfiles

internal fun Project.findXMakeBuildProfileFor(target: ExecutionTarget): XMakeBuildProfile? {
    val profileId = (target as? XMakeBuildProfileExecutionTarget)?.profileId ?: return null
    return xmakeBuildProfiles.findProfile(profileId)
}

internal fun Project.requireXMakeBuildProfileFor(target: ExecutionTarget): XMakeBuildProfile =
    findXMakeBuildProfileFor(target)
        ?: throw ExecutionException("Select an XMake build profile before running this configuration")

/** Uses the platform target, with an unambiguous fallback while only one profile exists. */
internal val Project.activeOrSingleXMakeBuildProfile: XMakeBuildProfile?
    get() = findXMakeBuildProfileFor(ExecutionTargetManager.getActiveTarget(this))
        ?: xmakeBuildProfiles.profiles.singleOrNull()
