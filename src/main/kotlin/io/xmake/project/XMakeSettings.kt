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

package io.xmake.project

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "XMakeSettings", storages = [Storage("xmake.xml")])
class XMakeSettings : PersistentStateComponent<XMakeSettings.State> {
    data class State(
        var compileCommandsPath: String = "",
        var autoUpdateCompileCommands: Boolean = true,
        var autoLinkCompilationDatabase: Boolean = true
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): XMakeSettings =
            project.serviceOrNull() ?: error("Failed to get XMakeSettings for $project")
    }
}

val Project.xmakeSettings: XMakeSettings.State
    get() = XMakeSettings.getInstance(this).state
