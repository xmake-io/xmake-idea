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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.xmake.project.profile.XMakeBuildProfile
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/** Runs a command operation in the context of one build profile, without interleaving. */
internal suspend fun <T> Project.withProfileCommands(
    profile: XMakeBuildProfile,
    action: suspend XMakeCommandFactory.(XMakeExecutionService) -> T,
): T {
    val executionService = xmakeExecutionService
    return executionService.runExclusive {
        val commandFactory = XMakeCommandFactory(this@withProfileCommands, profile)
        commandFactory.action(executionService)
    }
}

/** Runs `xmake config`; failures are tolerated because targets can be queried without a fresh config. */
internal suspend fun XMakeCommandFactory.configureBestEffort(executionService: XMakeExecutionService) {
    val command = createConfigure()
    try {
        executionService.execute(command)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.debug(
            "XMake configuration failed before an informational query: " +
                    command.commandLine.commandLineString,
            error,
        )
    }
}

/** Runs one `xmake show -l <name>` query. Older xmake versions reject the `--json` flag, so a
 *  failed query retries the plain listing, and that version keeps using the plain form. */
internal suspend fun XMakeCommandFactory.executeInfoQuery(
    queryName: String,
    executionService: XMakeExecutionService,
): String {
    if (toolkit.version in PLAIN_LISTING_VERSIONS) {
        return executionService.captureStandardOutput(createInfoQuery(queryName, jsonOutput = false))
    }
    return try {
        executionService.captureStandardOutput(createInfoQuery(queryName, jsonOutput = true))
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.debug("XMake ${toolkit.version} info query '$queryName' failed, retrying without --json", error)
        // A failed retry surfaces its own error: the plain listing is the one this version keeps
        // using, so its failure is the diagnostic one.
        val plain = executionService.captureStandardOutput(createInfoQuery(queryName, jsonOutput = false))
        PLAIN_LISTING_VERSIONS.add(toolkit.version)
        Log.warn("XMake ${toolkit.version} has no --json listings, using plain output")
        plain
    }
}

private val PLAIN_LISTING_VERSIONS = ConcurrentHashMap.newKeySet<String>()

private val Log = Logger.getInstance("io.xmake.run.command.XMakeProfileCommands")

internal const val DEFAULT_BUILD_TARGET = "default"
