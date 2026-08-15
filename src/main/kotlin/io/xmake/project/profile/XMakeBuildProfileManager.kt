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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.utils.Logger

@Service(Service.Level.PROJECT)
@State(name = "XMakeBuildProfiles", storages = [Storage("xmake.xml")])
class XMakeBuildProfileManager(private val project: Project) :
    PersistentStateComponent<XMakeBuildProfileManager.State> {
    data class State(
        var profiles: MutableList<XMakeBuildProfile> = mutableListOf(),
    )

    private val stateLock = Any()
    private var profileState = State()

    override fun getState(): State = synchronized(stateLock) {
        profileState.detachedCopy()
    }

    override fun noStateLoaded() {
        val initialState = createDefaultState()
        synchronized(stateLock) {
            profileState = initialState
        }
    }

    override fun loadState(state: State) {
        // A malformed ID cannot be remapped safely because run configurations may still refer to it.
        // Isolate that record and let the next state write remove it from the persisted snapshot.
        val loadedProfiles = XMakeBuildProfile.normalize(state.profiles).toMutableList()
        val discardedIds = state.profiles.map(XMakeBuildProfile::id).toSet() -
                loadedProfiles.map(XMakeBuildProfile::id).toSet()
        if (discardedIds.isNotEmpty()) {
            Logger.w(TAG, "Discarding ${discardedIds.size} malformed or duplicate build profiles: IDs $discardedIds")
        }
        val loadedState = if (loadedProfiles.isEmpty()) createDefaultState() else State(loadedProfiles)
        synchronized(stateLock) {
            profileState = loadedState
        }
    }

    val profiles: List<XMakeBuildProfile>
        get() = synchronized(stateLock) {
            profileState.profiles.map(XMakeBuildProfile::copy)
        }

    fun findProfile(id: String): XMakeBuildProfile? = synchronized(stateLock) {
        profileState.profiles.firstOrNull { it.id == id }?.copy()
    }

    fun replaceProfiles(profiles: List<XMakeBuildProfile>) {
        require(profiles.isNotEmpty()) { "At least one XMake build profile is required" }
        require(profiles.all { XMakeBuildProfile.isValidId(it.id) }) { "XMake build profile IDs are invalid" }
        require(profiles.map { it.id }.distinct().size == profiles.size) { "XMake build profile IDs must be unique" }
        require(profiles.all { it.name.isNotBlank() }) { "XMake build profile names must not be blank" }
        require(profiles.map { it.name.trim() }.distinct().size == profiles.size) {
            "XMake build profile names must be unique"
        }

        val replacement = profiles
            .map { it.copy(name = it.name.trim()) }
            .toMutableList()
        val changed = synchronized(stateLock) {
            if (profileState.profiles == replacement) {
                false
            } else {
                profileState = State(replacement)
                true
            }
        }
        if (!changed) return

        publishProfilesChanged()
    }

    internal fun handleToolkitChanges() {
        val toolkitManager = ToolkitManager.getInstance()
        val shouldPublish = synchronized(stateLock) {
            val updatedProfiles = profileState.profiles.map { profile ->
                val toolkitId = profile.toolkitId
                if (toolkitId != null && !toolkitManager.isRegistered(toolkitId)) {
                    profile.copy(toolkitId = null)
                } else {
                    profile
                }
            }
            if (updatedProfiles == profileState.profiles) {
                false
            } else {
                profileState = State(updatedProfiles.toMutableList())
                true
            }
        }
        if (shouldPublish) publishProfilesChanged()
    }

    private fun publishProfilesChanged() {
        val publish = Runnable {
            if (!project.isDisposed) {
                project.messageBus.syncPublisher(TOPIC).profilesChanged()
            }
        }
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            publish.run()
        } else {
            application.invokeLater(publish)
        }
    }

    private fun createDefaultState(): State =
        State(mutableListOf(XMakeBuildProfile.createDefault(project)))

    fun interface Listener {
        fun profilesChanged()
    }

    companion object {
        private const val TAG = "XMakeBuildProfileManager"

        @Topic.ProjectLevel
        val TOPIC: Topic<Listener> = Topic.create("XMake build profiles changed", Listener::class.java)
    }
}

private fun XMakeBuildProfileManager.State.detachedCopy(): XMakeBuildProfileManager.State =
    XMakeBuildProfileManager.State(profiles.map(XMakeBuildProfile::copy).toMutableList())

val Project.xmakeBuildProfiles: XMakeBuildProfileManager
    get() = getService(XMakeBuildProfileManager::class.java)
        ?: error("Failed to get XMakeBuildProfileManager for $this")
