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

import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.OptionTag
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.ToolkitHostType
import org.jdom.Element
import java.util.UUID

/** Maps build settings stored in an old run configuration to a project build profile. */
internal fun readLegacyBuildSettingsAsProfile(
    element: Element,
    configurationName: String,
): XMakeBuildProfile? {
    if (!hasLegacyBuildSettings(element)) return null

    val legacySettings = LegacyBuildSettings().also { state ->
        XmlSerializer.deserializeInto(state, element)
    }
    return XMakeBuildProfile(
        id = profileIdForConfiguration(configurationName, element),
        name = configurationName,
        toolkitId = legacySettings.toolkit?.let(::migratedToolkitId),
        platform = legacySettings.platform,
        architecture = legacySettings.architecture,
        toolchain = legacySettings.toolchain,
        buildMode = legacySettings.buildMode,
        workingDirectory = legacySettings.workingDirectory,
        buildDirectory = legacySettings.buildDirectory,
        androidNdkDirectory = legacySettings.androidNdkDirectory,
        verbose = legacySettings.verbose,
        configureArguments = legacySettings.configureArguments,
    )
}

internal fun hasLegacyBuildSettings(element: Element): Boolean =
    LEGACY_BUILD_SETTING_TAGS.any { tag -> element.getChild(tag) != null }

internal fun removeLegacyBuildSettings(element: Element) {
    LEGACY_BUILD_SETTING_TAGS.forEach(element::removeChildren)
}

internal fun writeBuildProfileReference(element: Element, profileId: String) {
    element.removeChildren(BUILD_PROFILE_TAG)
    element.addContent(Element(BUILD_PROFILE_TAG).setAttribute("value", profileId))
}

/**
 * Maps a legacy run configuration to a deterministic profile ID derived from its name and build
 * settings, so pre-load conversion and the runtime fallback remain idempotent regardless of which
 * one runs first, and same-named configurations with different settings stay distinct.
 */
private fun profileIdForConfiguration(
    configurationName: String,
    element: Element,
): String =
    UUID.nameUUIDFromBytes(
        "xmake-legacy-profile-v1\u0000$configurationName\u0000${settingsFingerprint(element)}".toByteArray(Charsets.UTF_8),
    ).toString()

private fun settingsFingerprint(element: Element): String =
        element.children
        .filter { child -> child.name in LEGACY_BUILD_SETTING_TAGS }
        .joinToString(separator = "\u0000") { child -> JDOMUtil.write(child, "") }

/** Remaps a legacy run configuration toolkit to the ID used by the current registry. */
private fun migratedToolkitId(toolkit: Toolkit): String {
    val host = toolkit.host
    val backendId = if (host.type == ToolkitHostType.LOCAL) null else host.migratedBackendId
    return Toolkit.createId(ToolkitHost(host.type, backendId), toolkit.path)
}

// Tag names intentionally match the legacy run configuration XML format.
private class LegacyBuildSettings {
    @OptionTag(tag = "activatedToolkit")
    var toolkit: Toolkit? = null

    @OptionTag(tag = "platform")
    var platform: String = XMakeBuildProfile.USE_XMAKE_DEFAULT

    @OptionTag(tag = "architecture")
    var architecture: String = XMakeBuildProfile.USE_XMAKE_DEFAULT

    @OptionTag(tag = "toolchain")
    var toolchain: String = XMakeBuildProfile.USE_XMAKE_DEFAULT

    @OptionTag(tag = "mode")
    var buildMode: String = XMakeBuildProfile.DEFAULT_BUILD_MODE

    @OptionTag(tag = "workingDirectory")
    var workingDirectory: String = ""

    @OptionTag(tag = "buildDirectory")
    var buildDirectory: String = ""

    @OptionTag(tag = "androidNDKDirectory")
    var androidNdkDirectory: String = ""

    @OptionTag(tag = "enableVerbose")
    var verbose: Boolean = false

    @OptionTag(tag = "additionalConfiguration")
    var configureArguments: String = ""
}

private const val BUILD_PROFILE_TAG = "buildProfile"

private val LEGACY_BUILD_SETTING_TAGS = setOf(
    "activatedToolkit",
    "platform",
    "architecture",
    "toolchain",
    "mode",
    "workingDirectory",
    "buildDirectory",
    "androidNDKDirectory",
    "enableVerbose",
    "additionalConfiguration",
)
