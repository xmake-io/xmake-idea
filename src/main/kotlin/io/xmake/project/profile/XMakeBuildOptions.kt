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
package io.xmake.project.profile

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.Toolkit
import io.xmake.run.command.executeInfoQuery
import io.xmake.run.command.withProfileCommands
import io.xmake.utils.info.XMakeInfo
import io.xmake.utils.runCancellable
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

internal data class XMakeBuildOptions(
    val architectures: Map<String, List<String>> = emptyMap(),
    val buildModes: List<String> = emptyList(),
    val platforms: List<String> = emptyList(),
    val toolchains: Map<String, String> = emptyMap(),
)

/** Queries the build options available in one build profile's command context. The profile only
 *  supplies the command context (its XMAKE_CONFIGDIR is profile-scoped); `xmake show -l` listings
 *  do not read it, so any profile bound to the toolkit yields the same catalog. */
internal suspend fun Project.queryXMakeBuildOptions(
    profile: XMakeBuildProfile,
    toolkit: Toolkit,
): XMakeBuildOptions {
    return withContext(Dispatchers.IO) {
        withProfileCommands(profile, toolkit) { executionService ->
            coroutineScope {
                // One failed listing must not empty the whole catalog; the project script
                // or the host can fail one listing while the others answer.
                suspend fun <T> listing(name: String, query: suspend () -> T): Deferred<T?> = async {
                    runCancellable { query() }.getOrElse { error ->
                        Log.warn("Failed to load the XMake '$name' listing on ${toolkit.name}", error)
                        null
                    }
                }
                val architectures = listing("architectures") {
                    XMakeInfo.parseArchitectures(executeInfoQuery("architectures", executionService))
                }
                val buildModes = listing("buildmodes") {
                    XMakeInfo.parseBuildModes(executeInfoQuery("buildmodes", executionService))
                }
                val platforms = listing("platforms") {
                    XMakeInfo.parsePlatforms(executeInfoQuery("platforms", executionService))
                }
                val toolchains = listing("toolchains") {
                    XMakeInfo.parseToolchains(executeInfoQuery("toolchains", executionService))
                }
                XMakeBuildOptions(
                    architectures = architectures.await()?.toSortedMap() ?: emptyMap(),
                    buildModes = buildModes.await()?.distinct()?.sorted() ?: emptyList(),
                    platforms = platforms.await()?.distinct()?.sorted() ?: emptyList(),
                    toolchains = toolchains.await()?.toSortedMap() ?: emptyMap(),
                )
            }
        }
    }
}

private val Log = logger<XMakeBuildOptions>()
