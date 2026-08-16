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
import com.intellij.openapi.components.*
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
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

    private val scanner = ToolkitScanner()
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

    fun detectXMakeToolkits(project: Project?) {
        detectionJob = scope.launch {
            try {
                val hostsByType = scanner.getHostsByType(project)
                scanner.scan(project, hostsByType.values.flatten()).collect { result ->
                    result.toolkits.forEach { scannedToolkit ->
                        val toolkit = refreshMatchingRegistration(scannedToolkit) ?: scannedToolkit
                        fetchedToolkitsSet.removeIf { known -> known.location == toolkit.location }
                        fetchedToolkitsSet.add(toolkit)
                        listenerList.forEach { listener ->
                            listener.onToolkitDetected(ToolkitDetectEvent(toolkit))
                        }
                        Logger.i(TAG, "toolkit added: $toolkit")
                    }
                }
                listenerList.forEach { listener -> listener.onAllToolkitsDetected() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ProcessCanceledException) {
                throw error
            } catch (error: Exception) {
                Logger.e(TAG, "Failed to scan for XMake toolkits", error)
            }
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
        storage = State().apply {
            lastSelectedToolkitId = state.lastSelectedToolkitId
            state.registeredToolkits.mapTo(registeredToolkits) { toolkit ->
                toolkit.copy(isRegistered = true)
            }
        }
        storage.registeredToolkits.forEach { toolkit ->
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
        val registeredToolkit = toolkit.copy(
            id = existing?.id ?: toolkit.id,
            isRegistered = true,
            isAvailable = toolkit.isAvailable,
        )
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
        val refreshedToolkit = toolkit.copy(
            id = registeredToolkit.id,
            isRegistered = true,
            isAvailable = toolkit.isAvailable,
        )
        storage.registeredToolkits.remove(registeredToolkit)
        storage.registeredToolkits.add(refreshedToolkit)
        return refreshedToolkit
    }

    fun getRegisteredToolkits(): List<Toolkit> {
        val sshHostIds = ToolkitHostExtension.forHostType(SSH)
            ?.getHosts(null)
            .orEmpty()
            .mapTo(mutableSetOf()) { host -> host.id }
        return storage.registeredToolkits.filter { toolkit ->
            !toolkit.requiresBackend || toolkit.host.id in sshHostIds
        }
//            .filterNot { (it.host.type == SSH && PlatformUtils.isCommunityEdition()) }
    }

    companion object {
        private const val TAG = "ToolkitManager"
        fun getInstance(): ToolkitManager = serviceOrNull() ?: throw IllegalStateException()
    }
}
