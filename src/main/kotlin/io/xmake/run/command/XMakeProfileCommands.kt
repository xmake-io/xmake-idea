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

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.toolkit.Toolkit
import io.xmake.utils.runCancellable
import java.util.concurrent.ConcurrentHashMap

/** Runs a command operation in the context of one build profile, without interleaving. */
internal suspend fun <T> Project.withProfileCommands(
    profile: XMakeBuildProfile,
    toolkit: Toolkit? = null,
    action: suspend XMakeCommandFactory.(XMakeExecutionService) -> T,
): T {
    val executionService = xmakeExecutionService
    return executionService.runExclusive {
        val commandFactory = XMakeCommandFactory(this@withProfileCommands, profile, toolkit)
        commandFactory.action(executionService)
    }
}

/** Runs `xmake config`; failures are tolerated because targets can be queried without a fresh config. */
internal suspend fun XMakeCommandFactory.configureBestEffort(executionService: XMakeExecutionService) {
    val command = createConfigure()
    runCancellable {
        executionService.executeSilently(command)
    }.onFailure { error ->
        Log.debug(
            "Configure failed before an informational query: " +
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
    if (toolkit.plainListingKey() in PLAIN_LISTING_TOOLKITS) {
        return executionService.captureStandardOutput(createInfoQuery(queryName, jsonOutput = false))
    }
    return runCancellable {
        executionService.captureStandardOutput(createInfoQuery(queryName, jsonOutput = true))
    }.getOrElse { error ->
        Log.debug(
            "XMake ${toolkit.version} info query '$queryName' " +
                    "failed, retrying without --json",
            error,
        )
        // A failed retry surfaces its own error: the plain listing is the one this version keeps
        // using, so its failure is the diagnostic one.
        val plain = executionService.captureStandardOutput(createInfoQuery(queryName, jsonOutput = false))
        PLAIN_LISTING_TOOLKITS.add(toolkit.plainListingKey())
        Log.warn("XMake ${toolkit.version} has no --json listings, using plain output")
        plain
    }
}

/** Identifies one installation at one version, so an in-place upgrade (new version)
 *  retries `--json` on its own. */
private fun Toolkit.plainListingKey(): Pair<String, String> = id to version

private val PLAIN_LISTING_TOOLKITS = ConcurrentHashMap.newKeySet<Pair<String, String>>()

private val Log = fileLogger()

internal const val DEFAULT_BUILD_TARGET = "default"
