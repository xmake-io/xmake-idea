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
import io.xmake.project.directory.LegacyProjectDirectory
import io.xmake.project.directory.XMakeProjectDirectoryState
import io.xmake.project.directory.migrateLegacyDirectories
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.XMakeBuildProfileXml
import io.xmake.project.toolkit.ToolkitManager
import org.jdom.Element
import java.nio.file.Files
import java.nio.file.Path

/**
 * Migrates the build settings stored in old XMake run configurations into project-owned
 * profiles and directories. Runs once before the project loads: it rewrites run configuration
 * files and merges the imported state into xmake.xml.
 */
class XMakeProjectSettingsConverterProvider : ConverterProvider() {
    override fun getConversionDescription(): String =
        "XMake build settings were moved from run configurations to project-owned state"

    override fun createConverter(context: ConversionContext): ProjectConverter =
        XMakeProjectSettingsConverter(context)
}

private class XMakeProjectSettingsConverter(
    private val context: ConversionContext,
) : ProjectConverter() {

    private val importedProfiles = mutableListOf<XMakeBuildProfile>()
    private val importedProjectDirectories = mutableListOf<LegacyProjectDirectory>()

    override fun createRunConfigurationsConverter(): ConversionProcessor<RunManagerSettings> =
        object : ConversionProcessor<RunManagerSettings>() {
            override fun isConversionNeeded(settings: RunManagerSettings): Boolean =
                settings.xmakeRunConfigurations().any { element -> hasLegacyBuildSettings(element) }

            override fun process(settings: RunManagerSettings) {
                settings.xmakeRunConfigurations().forEach { configuration ->
                    val name = configuration.getAttributeValue("name").orEmpty()
                    readLegacyBuildSettings(configuration, name)?.let { migratedSettings ->
                        importedProfiles += migratedSettings.profile
                        migratedSettings.legacyProjectDirectory?.let(importedProjectDirectories::add)
                        writeBuildProfileReference(configuration, migratedSettings.profile.id)
                        removeLegacyBuildSettings(configuration)
                    }
                }
            }
        }

    override fun getAdditionalAffectedFiles(): Collection<Path> {
        return if (Files.exists(settingsFile)) listOf(settingsFile) else emptyList()
    }

    override fun postProcessingFinished() {
        if (importedProfiles.isEmpty() && importedProjectDirectories.isEmpty()) return
        XMakeProjectSettingsFile(settingsFile)
            .mergeImportedState(importedProfiles, importedProjectDirectories)
    }

    private val settingsFile: Path
        get() = context.settingsBaseDir?.resolve(SETTINGS_FILE)
            ?: error("Cannot locate the project settings directory")

    private companion object {
        const val SETTINGS_FILE = "xmake.xml"
    }
}

private fun RunManagerSettings.xmakeRunConfigurations(): List<Element> =
    runConfigurations.filter { element -> element.getAttributeValue("type") == XMAKE_CONFIGURATION_TYPE }

private const val XMAKE_CONFIGURATION_TYPE = "XMakeRunConfiguration"

/** Reads, merges, and writes project-owned XMake state in xmake.xml. */
private class XMakeProjectSettingsFile(private val path: Path) {

    fun mergeImportedState(
        importedProfiles: List<XMakeBuildProfile>,
        importedProjectDirectories: List<LegacyProjectDirectory>,
    ) {
        val root = loadOrCreate()
        val profilesComponent = findOrCreateComponent(root, PROFILES_COMPONENT)
        val existingLegacyDirectories = XMakeBuildProfileXml.legacyProjectDirectories(profilesComponent) { toolkitId ->
            ToolkitManager.getInstance().registeredToolkit(toolkitId)
        }
        val legacyProjectDirectories = existingLegacyDirectories + importedProjectDirectories
        val existingProfiles = XMakeBuildProfileXml.readProfiles(profilesComponent)
        val mergedProfiles = mergeProfiles(existingProfiles, importedProfiles)
        XMakeBuildProfileXml.writeProfiles(profilesComponent, mergedProfiles)
        if (legacyProjectDirectories.isNotEmpty()) {
            writeMigratedDirectories(root, legacyProjectDirectories)
        }
        JDOMUtil.write(root, path)
    }

    private fun loadOrCreate(): Element =
        if (Files.exists(path)) {
            JDOMUtil.load(path)
        } else {
            Element("project").setAttribute("version", "4")
        }

    private fun findOrCreateComponent(root: Element, name: String): Element =
        root.getChildren("component")
            .firstOrNull { element -> element.getAttributeValue("name") == name }
            ?: Element("component").setAttribute("name", name).also(root::addContent)

    private fun mergeProfiles(
        existing: List<XMakeBuildProfile>,
        imported: List<XMakeBuildProfile>,
    ): List<XMakeBuildProfile> {
        val mergedProfiles = existing.toMutableList()
        // Use the same effective order and naming rules as the project service. Keep the raw
        // existing records in the file; they may still be referenced by an older run config.
        val normalizedExisting = XMakeBuildProfile.normalize(existing)
        val knownIds = normalizedExisting.mapTo(mutableSetOf(), XMakeBuildProfile::id)
        val knownNames = normalizedExisting.mapTo(mutableSetOf()) { profile -> profile.name }
        imported.forEach { profile ->
            if (!XMakeBuildProfile.isValidId(profile.id) || !knownIds.add(profile.id)) return@forEach
            val uniqueProfile = profile.copy(name = XMakeBuildProfile.uniqueName(profile.name, knownNames))
            knownNames += uniqueProfile.name
            mergedProfiles += uniqueProfile
        }
        return mergedProfiles
    }

    private fun writeMigratedDirectories(
        root: Element,
        legacyDirectories: List<LegacyProjectDirectory>,
    ) {
        val directoryComponent = findOrCreateComponent(root, DIRECTORY_COMPONENT)
        val persistedState = XMakeProjectDirectoryState()
        XmlSerializer.deserializeInto(persistedState, directoryComponent)

        val migratedState = persistedState.migrateLegacyDirectories(legacyDirectories)
        if (migratedState == persistedState) return

        directoryComponent.removeContent()
        XmlSerializer.serializeInto(migratedState, directoryComponent)
    }

    private companion object {
        const val PROFILES_COMPONENT = "XMakeBuildProfiles"
        const val DIRECTORY_COMPONENT = "XMakeProjectDirectory"
    }
}
