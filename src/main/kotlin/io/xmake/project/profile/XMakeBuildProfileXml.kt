package io.xmake.project.profile

import com.intellij.openapi.diagnostic.logger
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

    /** Reads directories that older versions stored on individual build profiles, keyed by
     *  profile id so unresolved ones can be re-attached on the next write. */
    fun readLegacyWorkingDirectories(element: Element): Map<String, PendingLegacyDirectory> =
        profileElements(element).mapNotNull { profile ->
            val directory = optionValue(profile, WORKING_DIRECTORY_OPTION)?.takeUnless(String::isBlank)
                ?: return@mapNotNull null
            val profileId = optionValue(profile, ID_OPTION) ?: return@mapNotNull null
            profileId to PendingLegacyDirectory(optionValue(profile, TOOLKIT_ID_OPTION), directory)
        }.toMap()

    /** Splits pending legacy directories into migratable ones and those whose toolkit is not
     *  registered yet; a missing toolkit id means a local directory, which always migrates. */
    fun resolveLegacyWorkingDirectories(
        pending: Map<String, PendingLegacyDirectory>,
        toolkitForId: (String) -> Toolkit?,
    ): Pair<List<LegacyProjectDirectory>, Map<String, PendingLegacyDirectory>> {
        val directories = mutableListOf<LegacyProjectDirectory>()
        val unresolved = linkedMapOf<String, PendingLegacyDirectory>()
        pending.forEach { (profileId, entry) ->
            val toolkit = entry.toolkitId?.let(toolkitForId)
            if (entry.toolkitId == null || toolkit != null) {
                directories += LegacyProjectDirectory(toolkit, entry.directory)
            } else {
                unresolved[profileId] = entry
            }
        }
        return directories to unresolved
    }

    /** Re-attaches unresolved legacy working directory options to their profile elements so a
     *  later session can retry once the toolkit is registered. */
    fun writeLegacyWorkingDirectories(element: Element, pending: Map<String, PendingLegacyDirectory>) {
        if (pending.isEmpty()) return
        profileElements(element).forEach { profile ->
            val profileId = optionValue(profile, ID_OPTION) ?: return@forEach
            val entry = pending[profileId] ?: return@forEach
            profile.addContent(
                Element("option")
                    .setAttribute("name", WORKING_DIRECTORY_OPTION)
                    .setAttribute("value", entry.directory),
            )
        }
    }

    /** A legacy working directory whose toolkit could not be resolved yet. */
    data class PendingLegacyDirectory(val toolkitId: String?, val directory: String)

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
    private const val ID_OPTION = "id"
    private const val TOOLKIT_ID_OPTION = "toolkitId"
    private const val WORKING_DIRECTORY_OPTION = "workingDirectory"
}
