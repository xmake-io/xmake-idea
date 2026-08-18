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
package io.xmake.project.target

import com.intellij.openapi.project.Project
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.run.command.configureBestEffort
import io.xmake.run.command.executeInfoQuery
import io.xmake.run.command.withProfileCommands
import io.xmake.utils.info.XMakeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Discovers the XMake build targets that are available for a build profile. */
internal suspend fun Project.discoverXMakeBuildTargets(profile: XMakeBuildProfile): List<String> =
    withContext(Dispatchers.IO) {
        withProfileCommands(profile) { executionService ->
            configureBestEffort(executionService)
            XMakeInfo().parseTargets(executeInfoQuery("targets", executionService))
        }
    }
