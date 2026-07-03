package io.xmake.project

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "XMakeSettings", storages = [Storage("xmake.xml")])
class XMakeSettings : PersistentStateComponent<XMakeSettings.State> {
    data class State(
        var compileCommandsPath: String = "",
        var autoUpdateCompileCommands: Boolean = false,
        var autoReloadConfigOnSwitch: Boolean = true
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): XMakeSettings = project.getService(XMakeSettings::class.java)
    }
}

val Project.xmakeSettings: XMakeSettings
    get() = XMakeSettings.getInstance(this)
