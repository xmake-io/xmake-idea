package io.xmake.project

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "XMakeSettings", storages = [Storage("xmake.xml")])
class XMakeSettings : PersistentStateComponent<XMakeSettings.State> {
    data class State(
        var compileCommandsPath: String = "",
        var autoUpdateCompileCommands: Boolean = false,
        var autoReloadConfigOnSwitch: Boolean = true,

        // Project-level xmake configuration (the `xmake f` inputs), decoupled from any run
        // configuration so it works with CLion-native run configs selected. Edited via the XMake
        // Config tool window and the toolbar mode dropdown; consumed by XMakeConfiguration.
        var buildMode: String = "release",
        var platform: String = "default",
        var architecture: String = "default",
        var toolchain: String = "default",
        var buildDirectory: String = "",
        var additionalConfiguration: String = "",
        var verbose: Boolean = false,
        var androidNDKDirectory: String = "",
        // Identity (Toolkit.id) of the active toolkit; resolved via ToolkitManager.
        var activeToolkitId: String = ""
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
