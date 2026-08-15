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
 * @author      ruki
 * @file        ToolkitManager.kt
 *
 */
package io.xmake.project.toolkit

import com.intellij.execution.RunManager
import com.intellij.execution.processTools.getBareExecutionResult
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WSLUtil
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.system.OS
import com.intellij.util.xmlb.annotations.XCollection
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.run.XMakeRunConfiguration
import io.xmake.utils.execute.*
import io.xmake.utils.extension.ToolkitHostExtension
import io.xmake.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

@Service
@State(name = "toolkits", storages = [Storage("xmakeToolkits.xml")])
class ToolkitManager(private val scope: CoroutineScope) : PersistentStateComponent<ToolkitManager.State> {

    private val EP_NAME: ExtensionPointName<ToolkitHostExtension> =
        ExtensionPointName("io.xmake.toolkitHostExtension")

    val fetchedToolkitsSet = mutableSetOf<Toolkit>()
    private lateinit var detectionJob: Job
    private lateinit var validateJob: Job
    private var storage: State = State()
    private val listenerList = mutableListOf<ToolkitDetectedListener>()

    class State{
        @XCollection(propertyElementName = "registeredToolKits")
        val registeredToolkits = mutableSetOf<Toolkit>()
        var lastSelectedToolkitId: String? = null
    }

    interface ToolkitDetectedListener : EventListener {
        fun onToolkitDetected(e: ToolkitDetectEvent)
        fun onAllToolkitsDetected()
    }

    class ToolkitDetectEvent(source: Toolkit) : EventObject(source)

    init {
        scope.launch {
            // Cache the list of installed distributions
            getInstalledWslDistributions()
        }
    }

    private fun toolkitHostFlow(project: Project? = null): Flow<ToolkitHost> = flow {
        val wslDistributions = scope.async { getInstalledWslDistributions() }

            emit(ToolkitHost().also { host -> Logger.i(TAG, "emit host: $host") })

            wslDistributions.await().forEach {
                emit(ToolkitHost.wsl(it).also { host -> Logger.i(TAG, "emit host: $host") })
            }

        EP_NAME.extensions.filter { it.KEY == "SSH" }.forEach {
            it.getToolkitHosts(project).forEach {
                emit(it).also { host -> Logger.i(TAG, "emit host: $host") }
            }
        }
    }

    private fun getInstalledWslDistributions(): List<WSLDistribution> {
        if (ApplicationManager.getApplication() == null) {
            return emptyList()
        }

        if (!WSLUtil.isSystemCompatible()) {
            return emptyList()
        }

        return try {
            WslDistributionManager.getInstance().installedDistributions
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(TAG, e.message ?: "Failed to read WSL distributions")
            emptyList()
        }
    }

    private fun detectToolkitLocation(host: ToolkitHost): Flow<String> = flow {
        val process = probeXmakeLocCommand.let {
            when (host.type) {
                LOCAL -> (if (OS.CURRENT == OS.Windows) probeXmakeLocCommandOnWin else it).createLocalProcess()
                WSL -> it.createWslProcess(host.backend as WSLDistribution)
                SSH -> with(EP_NAME.extensions.first { it.KEY == "SSH" }) { it.createProcess(host) }
            }
        }

        with(process.getBareExecutionResult()){
            Logger.i(TAG, "Host: ${host.type} ExitCode: $exitCode Output: ${stdOut.toString(Charsets.UTF_8)}")
            val paths = stdOut.toString(Charsets.UTF_8)
                .split(Regex("\\r\\n|\\n|\\r"))
                .filterNot { it.isBlank() || it.contains("not found") }
                .distinct()
            paths.forEach { emit(it); Logger.i(TAG, "emit path on ${host.type}: $it") }
        }
    }

    private fun detectToolkitVersion(host: ToolkitHost, path: String): Flow<String> = flow {
        val process = probeXmakeVersionCommand.withExePath(path).let {
            when (host.type) {
                LOCAL -> it.createLocalProcess()
                WSL -> it.createWslProcess(host.backend as WSLDistribution)
                SSH -> with(EP_NAME.extensions.first { it.KEY == "SSH" }) { it.createProcess(host) }
            }
        }
        val (stdout, exitCode) = runProcess(process)
        val versionString = stdout.getOrElse { "" }.split(Regex(",")).first().split(" ").last()
        Logger.i(TAG, "ExitCode: $exitCode Version: $versionString")
        emit(versionString)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun detectXMakeToolkits(project: Project?) {
        detectionJob = scope.launch {
            val toolkitFlow = toolkitHostFlow(project)

            val pathFlow = toolkitFlow.flatMapMerge { host ->
                detectToolkitLocation(host).catch {
                    Logger.w(TAG, it.message ?: "Unknown error")
                }.flowOn(Dispatchers.IO).buffer()
                    .distinctUntilChanged()
                    .filterNot { it.isBlank() }
                    .onEach { Logger.i(TAG, "output path: $it") }
                    .map { path -> host to path }
            }.flowOn(Dispatchers.Default).buffer()

            val versionFlow = pathFlow.flatMapMerge { (host, path) ->
                Logger.i(TAG, "detecting version: host: $host, path: $path")
                detectToolkitVersion(host, path).catch {
                    Logger.w(TAG, it.message ?: "Unknown error")
                }.flowOn(Dispatchers.IO).buffer().filterNot { it.isBlank() }.map { versionString ->
                    when (host.type) {
                        LOCAL -> {
                            val name = OS.CURRENT.name
                            Toolkit(name, host, path, versionString)
                        }

                        WSL -> {
                            val wslDistribution = host.backend as WSLDistribution
                            val name = wslDistribution.presentableName
                            Toolkit(name, host, path, versionString)
                        }

                        SSH -> {
                            EP_NAME.extensions.first { it.KEY == "SSH" }
                                .createToolkit(host, path, versionString)
                        }
                    }.apply { this.isRegistered = true; this.isValid = true }
                }
            }.flowOn(Dispatchers.Default).buffer()

            versionFlow.collect { scannedToolkit ->
                // Todo: Consider cache
                val toolkit = refreshMatchingRegistration(scannedToolkit) ?: scannedToolkit
                fetchedToolkitsSet.removeIf { known -> known.location == toolkit.location }
                fetchedToolkitsSet.add(toolkit)
                listenerList.forEach { listener ->
                    listener.onToolkitDetected(ToolkitDetectEvent(toolkit))
                }
                Logger.i(TAG, "toolkit added: $toolkit")
            }
            listenerList.forEach { it.onAllToolkitsDetected() }
        }
    }

    fun cancelDetection() {
        scope.launch {
            detectionJob.cancel()
        }
    }

    // Todo: Validate toolkit.
    fun validateXMakeToolkit() {
        scope.launch {
            try {
                validateJob?.cancel()
                validateJob = launch { validateToolkitsImpl() }
            } catch (e: Exception) {
                Logger.e(TAG, "Error", e)
            }
        }
    }

    // Todo: Validate toolkit.
    fun validateToolkits(){
        detectionJob?.cancel()
        detectionJob = scope.launch {
            try {
                validateToolkitsImpl()
            } catch (e: Exception) {
                Logger.e(TAG, "Error", e)
            }
        }
    }
    
    // Actual implementation of toolkit validation
    private fun validateToolkitsImpl() {
        // TODO: Implement actual validation logic
        Logger.i(TAG, "Validating toolkits...")
        // This would contain the actual validation implementation
    }

    // Todo: Validate toolkit.
    fun cancelValidation(){

    }

    fun addToolkitDetectedListener(listener: ToolkitDetectedListener) {
        listenerList.add(listener)
    }

    override fun getState(): State {
        return storage
    }

    override fun loadState(state: State) {
        storage = state
        state.registeredToolkits.forEach { toolkit ->
            toolkit.isRegistered = true
            loadToolkit(toolkit)
            fetchedToolkitsSet.removeIf { known -> known.location == toolkit.location }
            fetchedToolkitsSet.add(toolkit)
        }
    }

    private fun loadToolkit(toolkit: Toolkit) {
        scope.launch(Dispatchers.IO) {
            toolkit.host.loadBackend()
            joinAll()
        }
    }

    fun registerToolkit(toolkit: Toolkit) {
        val existing = findRegisteredToolkit(toolkit)
        val registeredToolkit = toolkit.copy(id = existing?.id ?: toolkit.id).apply {
            isRegistered = true
            isValid = toolkit.isValid
        }
        toolkit.isRegistered = true
        existing?.let(storage.registeredToolkits::remove)
        storage.registeredToolkits.add(registeredToolkit)
        loadToolkit(registeredToolkit)
        Logger.i(TAG, "load registered toolkit: ${registeredToolkit.name}, ${registeredToolkit.id}")
    }

    // Todo: Increase robustness of this method
    fun unregisterToolkit(toolkit: Toolkit) {
        val registeredToolkit = findRegisteredToolkit(toolkit) ?: return
        if (storage.registeredToolkits.remove(registeredToolkit)) {
            ProjectManager.getInstance().openProjects.forEach { project ->
                RunManager.getInstance(project).allConfigurationsList.forEach {
                    if (it is XMakeRunConfiguration) {
                        if (it.runToolkit?.id == registeredToolkit.id)
                            it.runToolkit = null
                    }
                }
            }
        }
    }

    fun findRegisteredToolkitById(id: String): Toolkit? {
        return storage.registeredToolkits.find { it.id == id }
    }

    private fun findRegisteredToolkit(toolkit: Toolkit): Toolkit? =
        findRegisteredToolkitById(toolkit.id)
            ?: storage.registeredToolkits.firstOrNull { registered ->
                registered.location == toolkit.location
            }

    private fun refreshMatchingRegistration(toolkit: Toolkit): Toolkit? {
        val registeredToolkit = findRegisteredToolkit(toolkit) ?: return null
        val refreshedToolkit = toolkit.copy(id = registeredToolkit.id).apply {
            isRegistered = true
            isValid = toolkit.isValid
        }
        storage.registeredToolkits.remove(registeredToolkit)
        storage.registeredToolkits.add(refreshedToolkit)
        return refreshedToolkit
    }

    fun getRegisteredToolkits(): List<Toolkit> {
        return state.registeredToolkits.filter { toolkit ->
            !toolkit.isOnRemote ||
                    EP_NAME.extensions.filter { it.KEY == "SSH" }.fold(true) { acc, sshExtension ->
                        acc || sshExtension.filterRegistered()(toolkit)
                    }
        }
//            .filterNot { (it.host.type == SSH && PlatformUtils.isCommunityEdition()) }
    }

    companion object {
        private const val TAG = "ToolkitManager"
        fun getInstance(): ToolkitManager = serviceOrNull() ?: throw IllegalStateException()
    }
}
