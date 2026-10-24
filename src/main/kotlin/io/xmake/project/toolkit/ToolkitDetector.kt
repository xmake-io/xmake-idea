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
 * @file        ToolkitDetector.kt
 *
 */
package io.xmake.project.toolkit

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.xmake.utils.cli.CliInstallation
import io.xmake.utils.cli.locateXMakeCommand
import io.xmake.utils.cli.xmake
import io.xmake.utils.runCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration.Companion.seconds

/** Detects XMake installations on hosts and remembers the latest result for each host. */
internal class ToolkitDetector {
    internal data class DetectionResult(
        val host: ToolkitHost,
        val toolkits: List<Toolkit>,
        val executablePaths: Set<String>,
    )

    private val detectionResultsByHostId = linkedMapOf<ToolkitHost.Id, DetectionResult>()

    val toolkits: List<Toolkit>
        get() = detectionResultsByHostId.values.flatMap { result -> result.toolkits }

    val detectedPaths: Map<ToolkitHost.Id, Set<String>>
        get() = detectionResultsByHostId.mapValues { (_, result) -> result.executablePaths }

    /** Stores a host's detection facts, reporting whether the visible snapshot changed. */
    fun apply(result: DetectionResult): Boolean {
        val previous = detectionResultsByHostId[result.host.id]
        detectionResultsByHostId[result.host.id] = result
        return previous == null || previous != result
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun detect(hosts: List<ToolkitHost>, project: Project? = null): Flow<DetectionResult> =
        hosts.asFlow()
            .flatMapMerge { host ->
                flow { detectHost(host, project)?.let { emit(it) } }
            }
            .flowOn(Dispatchers.IO)
            .buffer()

    private suspend fun detectHost(host: ToolkitHost, project: Project?): DetectionResult? = runCancellable {
        val executablePaths = locateExecutables(host, project)
        DetectionResult(
            host = host,
            toolkits = detectInstallations(host, executablePaths, project),
            executablePaths = executablePaths.toSet(),
        )
    }.getOrElse { error ->
        Log.warn("Keeping previous toolkits for ${host.id.canonical}: ${error.message ?: "detection failed"}")
        null
    }

    private suspend fun detectInstallations(
        host: ToolkitHost,
        executablePaths: List<String>,
        project: Project?,
    ): List<Toolkit> =
        executablePaths.map { path ->
            Log.info("Detect version: host: $host, path: $path")
            runCancellable {
                createToolkit(host, path, detectVersion(host, path, project))
            }.getOrElse { error ->
                Log.warn(
                    "Marking XMake installation at $path on ${host.id.canonical} unavailable: " +
                            (error.message ?: "version detection failed"),
                )
                createToolkit(host, path, "").copy(isAvailable = false)
            }
        }

    private suspend fun locateExecutables(host: ToolkitHost, project: Project?): List<String> {
        val result = locateXMakeCommand(host).execute(project, DETECT_TIMEOUT)
        // The lookup's stdout is authoritative: `which` exits non-zero when any candidate is
        // missing and `where` exits non-zero when none is found, so the exit code carries no
        // verdict. An empty output means the host has no XMake and applies as an empty result,
        // which marks registered installations on that host unavailable.
        val paths = result.stdOut.lineSequence().filterNot(String::isBlank).distinct().toList()
        Log.info("Host: ${host.id.canonical} ExitCode: ${result.exitCode} Output: ${result.stdOut}")
        paths.forEach { path -> Log.info("Found path on ${host.id.canonical}: $path") }
        return paths
    }

    private suspend fun detectVersion(host: ToolkitHost, path: String, project: Project?): String {
        val result = xmake(CliInstallation(path, host)).version().execute(project, DETECT_TIMEOUT)
        val output = result.stdOut
        val version = XMAKE_VERSION_PATTERN.find(output)?.groupValues?.get(1).orEmpty()
        Log.info("Host: ${host.id.canonical} ExitCode: ${result.exitCode} Version: $version")
        if (result.exitCode != 0) {
            val detail = result.stdErr.trim().takeIf(String::isNotEmpty)?.let { ": $it" }.orEmpty()
            throw ExecutionException("XMake version query failed with exit code ${result.exitCode}$detail")
        }
        if (version.isBlank()) throw ExecutionException("XMake version output does not contain a version")
        return version
    }


    private fun createToolkit(host: ToolkitHost, path: String, version: String): Toolkit =
        Toolkit(
            name = host.displayName,
            host = host.toRuntimeHost(),
            path = path,
            version = version,
        )

    companion object {
        private val Log = logger<ToolkitDetector>()

        /** Bounds every detection wait; a hung SSH or WSL channel must not freeze a host's detection. */
        private val DETECT_TIMEOUT = 15.seconds

        private val XMAKE_VERSION_PATTERN = Regex("""xmake\s+(v[^,\s]+)""", RegexOption.IGNORE_CASE)
    }
}
