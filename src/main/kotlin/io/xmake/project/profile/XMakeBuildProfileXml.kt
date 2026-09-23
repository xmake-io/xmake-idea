package io.xmake.project.profile

import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Tag
import io.xmake.project.directory.LegacyProjectDirectory
import io.xmake.project.toolkit.Toolkit
import org.jdom.Element
import java.util.UUID

/** Maps the persisted build-profile XML schema to and from the runtime model. */
internal object XMakeBuildProfileXml {

    fun readProfiles(element: Element): List<XMakeBuildProfile> {
        val persistedState = PersistedState()
        XmlSerializer.deserializeInto(persistedState, element)
        return persistedState.toProfiles()
    }

    fun writeProfiles(element: Element, profiles: List<XMakeBuildProfile>) {
        element.removeContent()
        XmlSerializer.serializeInto(
            PersistedState(profiles.map(::PersistedProfile).toMutableList()),
            element,
        )
    }

    /** Reads directories that older versions stored on individual build profiles.
     *
     *  [toolkitForId] resolves the persisted toolkit identity. A missing toolkit is not mapped
     *  to `null`: without its host type a remote path cannot be migrated safely. */
    fun legacyProjectDirectories(
        element: Element,
        toolkitForId: (String) -> Toolkit?,
    ): List<LegacyProjectDirectory> =
        profileElements(element).mapNotNull { profile ->
            val legacyDirectory = optionValue(profile, WORKING_DIRECTORY_OPTION)
                ?.takeUnless(String::isBlank)
                ?: return@mapNotNull null

            val toolkitId = optionValue(profile, TOOLKIT_ID_OPTION)
            val toolkit = toolkitId?.let(toolkitForId)
            if (toolkitId != null && toolkit == null) return@mapNotNull null

            LegacyProjectDirectory(toolkit, legacyDirectory)
        }

    private fun profileElements(element: Element): List<Element> =
        element.children.flatMap { child ->
            if (child.name == PROFILE_ELEMENT_TAG) {
                listOf(child)
            } else {
                profileElements(child)
            }
        }

    private fun optionValue(profile: Element, name: String): String? =
        profile.getChildren("option")
            .firstOrNull { option -> option.getAttributeValue("name") == name }
            ?.let { option -> option.getAttributeValue("value") ?: option.text }

    data class PersistedState(
        var profiles: MutableList<PersistedProfile> = mutableListOf(),
    ) {
        fun toProfiles(): List<XMakeBuildProfile> = profiles.mapNotNull(PersistedProfile::toProfile)
    }

    /** The XMLB payload. It is separate from [XMakeBuildProfile] so obsolete fields can leave
     *  the runtime model without changing every profile editor. */
    @Tag("XMakeBuildProfile")
    data class PersistedProfile(
        var id: String = UUID.randomUUID().toString(),
        var name: String = XMakeBuildProfile.DEFAULT_PROFILE_NAME,
        var toolkitId: String? = null,
        var platform: String = XMakeBuildProfile.USE_XMAKE_DEFAULT,
        var architecture: String = XMakeBuildProfile.USE_XMAKE_DEFAULT,
        var toolchain: String = XMakeBuildProfile.USE_XMAKE_DEFAULT,
        var buildMode: String = XMakeBuildProfile.DEFAULT_BUILD_MODE,
        var buildDirectory: String = "",
        var androidNdkDirectory: String = "",
        var verbose: Boolean = false,
        var configureArguments: String = "",
    ) {
        constructor(profile: XMakeBuildProfile) : this(
            id = profile.id,
            name = profile.name,
            toolkitId = profile.toolkitId,
            platform = profile.platform,
            architecture = profile.architecture,
            toolchain = profile.toolchain,
            buildMode = profile.buildMode,
            buildDirectory = profile.buildDirectory,
            androidNdkDirectory = profile.androidNdkDirectory,
            verbose = profile.verbose,
            configureArguments = profile.configureArguments,
        )

        fun toProfile(): XMakeBuildProfile? {
            if (!XMakeBuildProfile.isValidId(id)) return null
            return XMakeBuildProfile(
                id = id,
                name = name,
                toolkitId = toolkitId,
                platform = platform,
                architecture = architecture,
                toolchain = toolchain,
                buildMode = buildMode,
                buildDirectory = buildDirectory,
                androidNdkDirectory = androidNdkDirectory,
                verbose = verbose,
                configureArguments = configureArguments,
            )
        }
    }

    private const val PROFILE_ELEMENT_TAG = "XMakeBuildProfile"
    private const val TOOLKIT_ID_OPTION = "toolkitId"
    private const val WORKING_DIRECTORY_OPTION = "workingDirectory"
}
