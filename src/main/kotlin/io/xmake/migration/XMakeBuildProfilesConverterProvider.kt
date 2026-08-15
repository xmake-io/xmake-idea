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
package io.xmake.migration

import com.intellij.conversion.ConversionContext
import com.intellij.conversion.ConversionProcessor
import com.intellij.conversion.ConverterProvider
import com.intellij.conversion.ProjectConverter
import com.intellij.conversion.RunManagerSettings
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.XMakeBuildProfileManager
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path

/**
 * Migrates the build settings stored in old XMake run configurations into project build
 * profiles. Runs once before the project loads: it rewrites the run configuration files
 * and merges the imported profiles into xmake.xml.
 */
class XMakeBuildProfilesConverterProvider : ConverterProvider() {
    override fun getConversionDescription(): String =
        "XMake build settings were moved from run configurations to project build profiles"

    override fun createConverter(context: ConversionContext): ProjectConverter =
        XMakeBuildProfilesConverter(context)
}

private class XMakeBuildProfilesConverter(
    private val context: ConversionContext,
) : ProjectConverter() {

    private val importedProfiles = mutableListOf<XMakeBuildProfile>()

    override fun createRunConfigurationsConverter(): ConversionProcessor<RunManagerSettings> =
        object : ConversionProcessor<RunManagerSettings>() {
            override fun isConversionNeeded(settings: RunManagerSettings): Boolean =
                settings.xmakeRunConfigurations().any { element -> hasLegacyBuildSettings(element) }

            override fun process(settings: RunManagerSettings) {
                settings.xmakeRunConfigurations().forEach { configuration ->
                    val name = configuration.getAttributeValue("name").orEmpty()
                    readLegacyBuildSettingsAsProfile(configuration, name)?.let { profile ->
                        importedProfiles += profile
                        writeBuildProfileReference(configuration, profile.id)
                        removeLegacyBuildSettings(configuration)
                    }
                }
            }
        }

    override fun getAdditionalAffectedFiles(): Collection<Path> {
        return if (Files.exists(profilesFile)) listOf(profilesFile) else emptyList()
    }

    override fun postProcessingFinished() {
        if (importedProfiles.isEmpty()) return
        XMakeBuildProfileFile(profilesFile).mergeImportedProfiles(importedProfiles)
    }

    private val profilesFile: Path
        get() = context.settingsBaseDir?.resolve(PROFILES_FILE)
            ?: error("Cannot locate the project settings directory")

    private companion object {
        const val PROFILES_FILE = "xmake.xml"
    }
}

private fun RunManagerSettings.xmakeRunConfigurations(): List<Element> =
    runConfigurations.filter { element -> element.getAttributeValue("type") == XMAKE_CONFIGURATION_TYPE }

private const val XMAKE_CONFIGURATION_TYPE = "XMakeRunConfiguration"

/** Reads, merges, and writes the project build profiles storage file. */
private class XMakeBuildProfileFile(private val path: Path) {

    fun mergeImportedProfiles(importedProfiles: List<XMakeBuildProfile>) {
        val root = loadOrCreate()
        val component = findOrCreateComponent(root)
        val mergedProfiles = mergeProfiles(existingProfiles(component), importedProfiles)
        writeProfiles(component, mergedProfiles)
        JDOMUtil.write(root, path)
    }

    private fun loadOrCreate(): Element =
        if (Files.exists(path)) {
            JDOMUtil.load(path)
        } else {
            Element("project").setAttribute("version", "4")
        }

    private fun findOrCreateComponent(root: Element): Element =
        root.getChildren("component")
            .firstOrNull { element -> element.getAttributeValue("name") == PROFILES_COMPONENT }
            ?: Element("component").setAttribute("name", PROFILES_COMPONENT).also(root::addContent)

    private fun existingProfiles(component: Element): List<XMakeBuildProfile> {
        val state = XMakeBuildProfileManager.State()
        XmlSerializer.deserializeInto(state, component)
        return state.profiles
    }

    private fun mergeProfiles(
        existing: List<XMakeBuildProfile>,
        imported: List<XMakeBuildProfile>,
    ): List<XMakeBuildProfile> {
        val result = existing.toMutableList()
        // Use the same effective order and naming rules as the project service. Keep the raw
        // existing records in the file; they may still be referenced by an older run config.
        val normalizedExisting = XMakeBuildProfile.normalize(existing)
        val knownIds = normalizedExisting.mapTo(mutableSetOf(), XMakeBuildProfile::id)
        val knownNames = normalizedExisting.mapTo(mutableSetOf()) { profile -> profile.name }
        imported.forEach { profile ->
            if (!XMakeBuildProfile.isValidId(profile.id) || !knownIds.add(profile.id)) return@forEach
            val unique = profile.copy(name = XMakeBuildProfile.uniqueName(profile.name, knownNames))
            knownNames += unique.name
            result += unique
        }
        return result
    }

    private fun writeProfiles(component: Element, profiles: List<XMakeBuildProfile>) {
        component.removeContent()
        val state = XMakeBuildProfileManager.State(profiles.toMutableList())
        XmlSerializer.serializeInto(state, component)
    }

    private companion object {
        const val PROFILES_COMPONENT = "XMakeBuildProfiles"
    }
}
