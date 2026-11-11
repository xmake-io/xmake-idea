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

package io.xmake.clion.compdb

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(
    name = "XMakeDerivedToolchains",
    storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)],
)
class XMakeDerivedToolchains : PersistentStateComponent<XMakeDerivedToolchains.State> {

    data class State(
        var names: MutableSet<String> = mutableSetOf(),
    )

    private val lock = Any()
    private var myState = State()

    override fun getState(): State = synchronized(lock) {
        State(myState.names.toMutableSet())
    }

    override fun loadState(state: State) {
        synchronized(lock) {
            myState = State(state.names.filter { it.isNotBlank() }.toMutableSet())
        }
    }

    fun contains(name: String): Boolean = synchronized(lock) { name in myState.names }

    fun add(name: String) {
        synchronized(lock) {
            myState.names += name
        }
    }

    companion object {
        fun getInstance(): XMakeDerivedToolchains =
            ApplicationManager.getApplication().service()
    }
}
