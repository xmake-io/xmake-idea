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
package io.xmake.project.toolkit

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.ExecutionResult
import com.intellij.execution.processTools.getBareExecutionResult
import com.intellij.execution.wsl.WSLUtil
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.ToolkitHostType.LOCAL
import io.xmake.project.toolkit.ToolkitHostType.SSH
import io.xmake.project.toolkit.ToolkitHostType.WSL
import io.xmake.utils.execute.createWslProcess
import io.xmake.utils.execute.probeXmakeLocCommand
import io.xmake.utils.execute.probeXmakeLocCommandOnWin
import io.xmake.utils.execute.probeXmakeVersionCommand
import io.xmake.utils.extension.ToolkitHostExtension
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

/** Reads current hosts, probes their XMake installations, and remembers the latest scan results. */
internal class ToolkitScanner {
    internal data class ScanResult(
        val host: ToolkitHost,
        val toolkits: List<Toolkit>,
        val executablePaths: Set<String>,
    )

    /** The latest successful scan result for each host. */
    private val scanResultsByHostId = linkedMapOf<ToolkitHost.Id, ScanResult>()

    val toolkits: List<Toolkit>
        get() = scanResultsByHostId.values.flatMap { result -> result.toolkits }

    fun find(id: String): Toolkit? =
        scanResultsByHostId.values.asSequence()
            .flatMap { result -> result.toolkits }
            .firstOrNull { toolkit -> toolkit.id == id }

    fun findByLocation(location: Toolkit.Location): Toolkit? =
        scanResultsByHostId.values.asSequence()
            .flatMap { result -> result.toolkits }
            .firstOrNull { toolkit -> toolkit.location == location }

    fun lastScannedExecutablePaths(hostId: ToolkitHost.Id): Set<String>? =
        scanResultsByHostId[hostId]?.executablePaths

    fun clear() {
        scanResultsByHostId.clear()
    }

    fun apply(result: ScanResult): List<Toolkit> {
        val previous = scanResultsByHostId[result.host.id]?.toolkits.orEmpty()
        val affected = linkedMapOf<String, Toolkit>()

        val scannedToolkits = result.toolkits.map { scannedToolkit ->
            val existing = previous.firstOrNull { candidate -> candidate.location == scannedToolkit.location }
            val next = existing?.takeIf { candidate -> candidate.isSameSnapshotAs(scannedToolkit) } ?: scannedToolkit
            if (next !== existing) affected[next.id] = next
            next
        }

        // A complete executable scan is authoritative: omitted paths are no longer present.
        previous
            .filter { previous -> previous.path !in result.executablePaths }
            .forEach { previous -> affected[previous.id] = previous }

        scanResultsByHostId[result.host.id] = result.copy(toolkits = scannedToolkits)
        return affected.values.toList()
    }

    fun applyHosts(hostsByType: Map<ToolkitHostType, List<ToolkitHost>>): List<Toolkit> {
        val hostIdsByType = hostsByType.mapValues { (_, hosts) ->
            hosts.mapTo(mutableSetOf(), ToolkitHost::id)
        }
        val affected = linkedMapOf<String, Toolkit>()

        val removedResults = scanResultsByHostId.values.filter { result ->
            val hostIds = hostIdsByType[result.host.type] ?: return@filter false
            result.host.id !in hostIds
        }
        val removedHostIds = removedResults.mapTo(mutableSetOf()) { result -> result.host.id }
        scanResultsByHostId.keys.removeAll(removedHostIds)
        removedResults.forEach { result ->
            result.toolkits.forEach { toolkit -> affected[toolkit.id] = toolkit }
        }
        return affected.values.toList()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun scan(project: Project?, hosts: List<ToolkitHost>): Flow<ScanResult> =
        hosts.asFlow()
            .flatMapMerge { host ->
                flow { scanHost(project, host)?.let { emit(it) } }
            }
            .flowOn(Dispatchers.IO)
            .buffer()

    /**
     * Returns current hosts by host type.
     *
     * A type is omitted when its provider fails, leaving that type's previous scan results intact.
     */
    suspend fun getHostsByType(project: Project?): Map<ToolkitHostType, List<ToolkitHost>> =
        withContext(Dispatchers.IO) {
            val hostsByType = linkedMapOf<ToolkitHostType, List<ToolkitHost>>()

            hostsByType[LOCAL] = listOf(ToolkitHost(LOCAL))

            if (WSLUtil.isSystemCompatible()) {
                try {
                    hostsByType[WSL] = WslDistributionManager.getInstance().installedDistributions
                        .map { distribution -> ToolkitHost.wsl(distribution) }
                        .onEach { host -> Log.info("read WSL host: $host") }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.warn("Failed to read WSL distributions", error)
                }
            } else {
                hostsByType[WSL] = emptyList()
            }

            val hostExtension = ToolkitHostExtension.forHostType(SSH)
            if (hostExtension != null) {
                try {
                    hostsByType[SSH] = hostExtension.getHosts(project)
                        .onEach { host -> Log.info("read SSH host: $host") }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.warn("Failed to read SSH hosts", error)
                }
            }

            hostsByType.mapValues { (_, hosts) -> hosts.distinctBy(ToolkitHost::id) }
        }

    private suspend fun scanHost(project: Project?, host: ToolkitHost): ScanResult? = try {
        val executablePaths = locateExecutables(project, host)
        ScanResult(
            host = host,
            toolkits = probeToolkits(project, host, executablePaths),
            executablePaths = executablePaths.toSet(),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Log.warn("Keeping previous toolkits for ${host.id.canonical}: ${error.message ?: "scan failed"}")
        null
    }

    private suspend fun probeToolkits(
        project: Project?,
        host: ToolkitHost,
        executablePaths: List<String>,
    ): List<Toolkit> {
        return executablePaths.map { path ->
            Log.info("probing version: host: $host, path: $path")
            try {
                createToolkit(host, path, probeVersion(project, host, path))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warn(
                    "Marking XMake installation at $path on ${host.id.canonical} unavailable: " +
                            (error.message ?: "version probe failed"),
                )
                createToolkit(host, path, "").copy(isAvailable = false)
            }
        }
    }

    private suspend fun locateExecutables(project: Project?, host: ToolkitHost): List<String> {
        val command = if (host.type == LOCAL && IS_WINDOWS_HOST) {
            probeXmakeLocCommandOnWin
        } else {
            probeXmakeLocCommand
        }
        val result = execute(project, host, command)
        val output = result.stdOut.toString(Charsets.UTF_8)
        val paths = output
            .lineSequence()
            .filterNot { line -> line.isBlank() || line.contains("not found") }
            .distinct()
            .toList()
        Log.info("Host: ${host.type} ExitCode: ${result.exitCode} Output: $output")
        paths.forEach { path -> Log.info("found path on ${host.type}: $path") }

        if (paths.isEmpty()) {
            // `where` and `which` return a non-zero exit code when XMake is not installed.
            return emptyList()
        }
        // Multi-path probes exit non-zero when any candidate is missing, so found paths are authoritative.
        return paths
    }

    private suspend fun probeVersion(project: Project?, host: ToolkitHost, path: String): String {
        val command = probeXmakeVersionCommand.withExePath(path)
        val result = execute(project, host, command)
        val output = result.stdOut.toString(Charsets.UTF_8)
        val version = XMAKE_VERSION_PATTERN.find(output)?.groupValues?.get(1).orEmpty()
        Log.info("Host: ${host.type} ExitCode: ${result.exitCode} Version: $version")

        if (result.exitCode != 0) {
            throw executionFailure(result)
        }
        if (version.isBlank()) {
            throw ExecutionException("XMake version output does not contain a version")
        }
        return version
    }

    private fun executionFailure(result: ExecutionResult): ExecutionException {
        val stderr = result.stdErr.toString(Charsets.UTF_8).trim()
        val detail = stderr.takeIf(String::isNotEmpty)?.let { text -> ": $text" }.orEmpty()
        return ExecutionException(
            "Failed to query the XMake version with exit code ${result.exitCode}$detail",
        )
    }

    private suspend fun execute(
        project: Project?,
        host: ToolkitHost,
        command: GeneralCommandLine,
    ) = when (host.type) {
        LOCAL -> runInterruptible(Dispatchers.IO) {
            ProcessBuilder(command.getCommandLineList(command.exePath)).start()
        }

        WSL -> command.createWslProcess(
            host.requireWslDistribution(),
            project,
        )

        SSH -> ToolkitHostExtension.requireForHostType(SSH).startProcess(host, command)
    }.getBareExecutionResult()

    private fun createToolkit(host: ToolkitHost, path: String, version: String): Toolkit =
        Toolkit(
            name = host.displayName,
            host = host.toRuntimeHost(),
            path = path,
            version = version,
        )

    companion object {
        private val Log = logger<ToolkitScanner>()

        private val IS_WINDOWS_HOST = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
        private val XMAKE_VERSION_PATTERN = Regex("""xmake\s+(v[^,\s]+)""", RegexOption.IGNORE_CASE)
    }
}
