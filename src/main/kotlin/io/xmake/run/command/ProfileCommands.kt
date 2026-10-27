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
package io.xmake.run.command

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitInstallationKey
import io.xmake.project.toolkit.installationKey
import io.xmake.utils.runCancellable
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException

/** The command context of one build profile: one xmake run at a time, with the version
 *  refusals ([XMakeProjectVersionError]) rethrown and the tolerated failures shared by
 *  every query. */
internal class ProfileCommands internal constructor(
    private val factory: XMakeCommandFactory,
    private val executor: XMakeExecutor,
) {

    /** One `xmake show -l <name>` listing. Older xmake versions reject the `--json` flag, so
     *  a failed listing retries the plain form, and that version keeps using it. A refusal
     *  caused by the project requiring a newer xmake becomes [XMakeProjectVersionError]. */
    suspend fun listing(name: String): String {
        if (factory.toolkit.installationKey() in PLAIN_LISTING_TOOLKITS) {
            return executor.captureStandardOutput(factory.createInfoQuery(name, jsonOutput = false))
        }
        return runCancellable {
            executor.captureStandardOutput(factory.createInfoQuery(name, jsonOutput = true))
        }.getOrElse { error ->
            Log.debug(
                "XMake ${factory.toolkit.version} listing '$name' failed, retrying without --json",
                error,
            )
            // A failed retry surfaces its own error: the plain listing is the one this version
            // keeps using, so its failure is the diagnostic one.
            val plain = executor.captureStandardOutput(factory.createInfoQuery(name, jsonOutput = false))
            PLAIN_LISTING_TOOLKITS.add(factory.toolkit.installationKey())
            Log.warn("XMake ${factory.toolkit.version} has no --json listings, using plain output")
            plain
        }
    }

    /** One listing parsed with [parse]; a failure warns and answers null, while a version
     *  refusal still throws so the caller can show it. */
    suspend fun <T> listing(name: String, parse: (String) -> T): T? =
        try {
            parse(listing(name))
        } catch (error: CancellationException) {
            throw error
        } catch (error: XMakeProjectVersionError) {
            throw error
        } catch (error: Exception) {
            Log.warn("Failed to load the XMake '$name' listing on ${factory.toolkit.name}", error)
            null
        }

    /** Runs `xmake config`; failures are tolerated because targets can be queried without a
     *  fresh config. */
    suspend fun configureBestEffort() {
        val command = factory.createConfigure()
        runCancellable { executor.executeSilently(command) }.onFailure { error ->
            Log.debug(
                "Configure failed before an informational query: " +
                        command.commandLine.commandLineString,
                error,
            )
        }
    }

}

/** Runs [action] in the command context of one build profile, without interleaving. */
internal suspend fun <T> Project.withProfileCommands(
    profile: XMakeBuildProfile,
    toolkit: Toolkit? = null,
    action: suspend ProfileCommands.() -> T,
): T {
    val executor = xmakeExecutor
    return executor.runExclusive {
        ProfileCommands(XMakeCommandFactory(this@withProfileCommands, profile, toolkit), executor).action()
    }
}

private val PLAIN_LISTING_TOOLKITS = ConcurrentHashMap.newKeySet<ToolkitInstallationKey>()

private val Log = logger<ProfileCommands>()
