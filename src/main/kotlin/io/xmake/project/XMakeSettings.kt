package io.xmake.project

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.XCollection

@Service(Service.Level.PROJECT)
@State(name = "XMakeSettings", storages = [Storage("xmake.xml")])
class XMakeSettings : PersistentStateComponent<XMakeSettings.State> {
    data class State(
        var compileCommandsPath: String = "",
        var autoUpdateCompileCommands: Boolean = false,
        var autoReloadConfigOnSwitch: Boolean = true,

        // Global knobs, orthogonal to the profiles: the build mode has its own toolbar dropdown,
        // verbose is an output preference; consumed by XMakeConfiguration.
        var buildMode: String = "release",
        var verbose: Boolean = false,

        // Named profiles bundling the remaining `xmake f` inputs (platform/arch/toolchain/...),
        // like CLion's CMake profiles. Managed in Settings > Build > Xmake, switched from the
        // toolbar profile dropdown.
        @field:XCollection(style = XCollection.Style.v2)
        var profiles: MutableList<XMakeProfile> = mutableListOf(),
        var activeProfileName: String = "",

        // Legacy flat configuration fields, kept only so old xmake.xml files deserialize; they are
        // migrated into a "Default" profile on load and then reset. Do not read them elsewhere.
        var platform: String = "default",
        var architecture: String = "default",
        var toolchain: String = "default",
        var buildDirectory: String = "",
        var additionalConfiguration: String = "",
        var androidNDKDirectory: String = "",

        // Identity (Toolkit.id) of the active toolkit; resolved via ToolkitManager.
        var activeToolkitId: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        migrateAndEnsureProfiles()
    }

    override fun noStateLoaded() {
        migrateAndEnsureProfiles()
    }

    /**
     * Guarantees at least one profile and a valid [State.activeProfileName]. On first load after
     * the flat-field era, the legacy fields seed a "Default" profile and are reset so the
     * serializer stops writing them.
     */
    private fun migrateAndEnsureProfiles() {
        val s = myState
        if (s.profiles.isEmpty()) {
            s.profiles.add(
                XMakeProfile(
                    name = DEFAULT_PROFILE_NAME,
                    platform = s.platform,
                    architecture = s.architecture,
                    toolchain = s.toolchain,
                    buildDirectory = s.buildDirectory,
                    additionalConfiguration = s.additionalConfiguration,
                    androidNDKDirectory = s.androidNDKDirectory,
                )
            )
            s.platform = "default"
            s.architecture = "default"
            s.toolchain = "default"
            s.buildDirectory = ""
            s.additionalConfiguration = ""
            s.androidNDKDirectory = ""
        }
        if (s.profiles.none { it.name == s.activeProfileName }) {
            s.activeProfileName = s.profiles.first().name
        }
    }

    /** The profile the toolbar dropdown points at; never null, even on hand-edited xmake.xml. */
    val activeProfile: XMakeProfile
        get() = myState.profiles.firstOrNull { it.name == myState.activeProfileName }
            ?: myState.profiles.firstOrNull()
            ?: XMakeProfile(name = DEFAULT_PROFILE_NAME)

    companion object {
        const val DEFAULT_PROFILE_NAME = "Default"

        fun getInstance(project: Project): XMakeSettings = project.getService(XMakeSettings::class.java)
    }
}

val Project.xmakeSettings: XMakeSettings
    get() = XMakeSettings.getInstance(this)
