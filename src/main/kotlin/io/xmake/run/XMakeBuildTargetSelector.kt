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
package io.xmake.run

import com.intellij.execution.ExecutionTargetListener
import com.intellij.execution.ExecutionTargetManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import io.xmake.project.directory.XMakeProjectDirectoryManager
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.XMakeBuildProfileManager
import io.xmake.project.profile.xmakeBuildProfiles
import io.xmake.project.target.discoverXMakeBuildTargets
import io.xmake.run.command.DEFAULT_BUILD_TARGET
import io.xmake.utils.ui.LiveModelComboBox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.DefaultComboBoxModel

@OptIn(ExperimentalCoroutinesApi::class)
class XMakeBuildTargetSelector(
    private val project: Project,
    parentDisposable: Disposable,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val buildTargetModel = DefaultComboBoxModel<String>()
    val component: LiveModelComboBox<String> = LiveModelComboBox(buildTargetModel)

    private var buildTargetProfileSnapshot: XMakeBuildProfile? = null
    private val targetRequests = Channel<XMakeBuildProfile>(Channel.CONFLATED)
    private var editedConfiguration: XMakeProfileRunConfiguration? = null

    val selectedTarget: String
        get() = buildTargetModel.selectedItem?.toString() ?: DEFAULT_BUILD_TARGET

    init {
        Disposer.register(parentDisposable) { scope.cancel() }
        scope.launch {
            targetRequests.consumeAsFlow()
                .transformLatest { profile ->
                    try {
                        emit(profile to project.discoverXMakeBuildTargets(profile))
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        // Keep the persisted target when profile discovery is unavailable.
                        Log.warn("Failed to load XMake targets for profile ${profile.id}", error)
                    }
                }
                .collect { (requestedProfile, buildTargets) ->
                    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                        if (project.isDisposed || buildTargetProfileSnapshot != requestedProfile) {
                            return@withContext
                        }
                        replaceBuildTargetChoices(selectedTarget, buildTargets)
                    }
                }
        }
        replaceBuildTargetChoices(DEFAULT_BUILD_TARGET, emptyList())
        val connection = project.messageBus.connect(parentDisposable)
        connection.subscribe(ExecutionTargetManager.TOPIC, ExecutionTargetListener {
            editedConfiguration?.let(::refreshBuildTargets)
        })
        connection.subscribe(XMakeBuildProfileManager.TOPIC, XMakeBuildProfileManager.Listener {
            editedConfiguration?.let(::refreshBuildTargets)
        })
        connection.subscribe(XMakeProjectDirectoryManager.TOPIC, XMakeProjectDirectoryManager.Listener {
            editedConfiguration?.let(::refreshBuildTargets)
        })
    }

    fun reset(configuration: XMakeProfileRunConfiguration) {
        editedConfiguration = configuration
        val selectedBuildTarget = configuration.runTarget.ifBlank { DEFAULT_BUILD_TARGET }
        if (buildTargetModel.getIndexOf(selectedBuildTarget) < 0) {
            buildTargetModel.addElement(selectedBuildTarget)
        }
        buildTargetModel.selectedItem = selectedBuildTarget
        refreshBuildTargets(configuration)
    }

    private fun refreshBuildTargets(configuration: XMakeProfileRunConfiguration) {
        val profile = configuration.preferredBuildProfileId
            ?.let(project.xmakeBuildProfiles::findProfile)
            ?: project.xmakeBuildProfiles.profiles.singleOrNull()
        if (profile == null) {
            resetBuildTargetChoices(configuration.runTarget)
        } else {
            requestBuildTargets(profile)
        }
    }

    private fun requestBuildTargets(profile: XMakeBuildProfile) {
        val requestedProfileSnapshot = profile.copy()
        if (buildTargetProfileSnapshot != requestedProfileSnapshot) {
            buildTargetProfileSnapshot = requestedProfileSnapshot
            replaceBuildTargetChoices(selectedTarget, emptyList())
        }
        targetRequests.trySend(requestedProfileSnapshot)
    }

    private fun resetBuildTargetChoices(selectedTarget: String) {
        buildTargetProfileSnapshot = null
        replaceBuildTargetChoices(selectedTarget, emptyList())
    }

    private fun replaceBuildTargetChoices(selectedTarget: String, buildTargets: Iterable<String>) {
        val selectedBuildTarget = selectedTarget.ifBlank { DEFAULT_BUILD_TARGET }
        val buildTargetChoices = buildList {
            add(DEFAULT_BUILD_TARGET)
            addAll(buildTargets.filter(String::isNotBlank))
            // Keep the persisted/custom target even when discovery does not return it.
            add(selectedBuildTarget)
        }.distinct()

        buildTargetModel.removeAllElements()
        buildTargetModel.addAll(buildTargetChoices)
        buildTargetModel.selectedItem = selectedBuildTarget
        component.refreshPopupFromModel()
    }

    private companion object {
        val Log = logger<XMakeBuildTargetSelector>()
    }
}
