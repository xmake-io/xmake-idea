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

package io.xmake.debug.clion.compdb

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag

@Service(Service.Level.PROJECT)
@State(
    name = "XMakeCompDBToolchainChoices",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class XMakeCompDBToolchainChoices : PersistentStateComponent<XMakeCompDBToolchainChoices.State> {

    @Tag("choice")
    data class Choice(
        @Attribute("environment") var environment: String = "",
        @Attribute("name") var name: String? = null,
    )

    data class State(
        var choices: MutableList<Choice> = mutableListOf(),
        var bindingAdopted: Boolean = false,
        var hasPendingChoice: Boolean = false,
        var pendingChoiceName: String? = null,
    )

    private val lock = Any()
    private var myState = State()

    override fun getState(): State = synchronized(lock) {
        State(
            myState.choices.map { it.copy() }.toMutableList(),
            myState.bindingAdopted,
            myState.hasPendingChoice,
            myState.pendingChoiceName,
        )
    }

    override fun loadState(state: State) {
        synchronized(lock) {
            myState = State(
                state.choices
                    .filter { it.environment.isNotBlank() }
                    .distinctBy { it.environment }
                    .toMutableList(),
                state.bindingAdopted,
                state.hasPendingChoice,
                state.pendingChoiceName,
            )
        }
    }

    fun stored(environment: String): ManualChoice? = synchronized(lock) {
        val choice = myState.choices.firstOrNull { it.environment == environment } ?: return null
        choice.name?.let(ManualChoice::Named) ?: ManualChoice.Default
    }

    fun store(environment: String, name: String?) {
        synchronized(lock) {
            val choice = myState.choices.firstOrNull { it.environment == environment }
            if (choice == null) {
                myState.choices += Choice(environment, name)
            } else {
                choice.name = name
            }
        }
    }

    fun clear(environment: String) {
        synchronized(lock) {
            myState.choices.removeAll { it.environment == environment }
        }
    }

    fun isBindingAdopted(): Boolean = synchronized(lock) { myState.bindingAdopted }

    fun markBindingAdopted() {
        synchronized(lock) {
            myState.bindingAdopted = true
        }
    }

    fun storePending(name: String?) {
        synchronized(lock) {
            myState.hasPendingChoice = true
            myState.pendingChoiceName = name
        }
    }

    fun takePending(): ManualChoice? = synchronized(lock) {
        if (!myState.hasPendingChoice) return null
        val name = myState.pendingChoiceName
        myState.hasPendingChoice = false
        myState.pendingChoiceName = null
        name?.let(ManualChoice::Named) ?: ManualChoice.Default
    }

    companion object {
        fun getInstance(project: Project): XMakeCompDBToolchainChoices = project.service()
    }
}

sealed interface ManualChoice {
    data object Default : ManualChoice
    data class Named(val name: String) : ManualChoice
}
