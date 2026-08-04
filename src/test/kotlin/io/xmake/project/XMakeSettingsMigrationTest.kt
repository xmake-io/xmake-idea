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
 *
 * @author      ruki
 * @file        XMakeSettingsMigrationTest.kt
 *
 */
package io.xmake.project

import com.intellij.util.xmlb.XmlSerializer
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class XMakeSettingsMigrationTest {

    @Test
    fun `legacy flat fields migrate into a Default profile`() {
        val settings = XMakeSettings()
        settings.loadState(XMakeSettings.State(
            platform = "mingw",
            architecture = "x86_64",
            toolchain = "clang",
            buildDirectory = "out",
            additionalConfiguration = "--cc=clang",
            androidNDKDirectory = "C:/ndk",
        ))

        val state = settings.state
        assertEquals("One migrated profile expected", 1, state.profiles.size)
        val profile = state.profiles.first()
        assertEquals(XMakeSettings.DEFAULT_PROFILE_NAME, profile.name)
        assertEquals("mingw", profile.platform)
        assertEquals("x86_64", profile.architecture)
        assertEquals("clang", profile.toolchain)
        assertEquals("out", profile.buildDirectory)
        assertEquals("--cc=clang", profile.additionalConfiguration)
        assertEquals("C:/ndk", profile.androidNDKDirectory)
        assertEquals(profile.name, state.activeProfileName)
        assertEquals(profile, settings.activeProfile)

        // Legacy carriers are reset so the serializer stops writing them.
        assertEquals("default", state.platform)
        assertEquals("default", state.architecture)
        assertEquals("default", state.toolchain)
        assertEquals("", state.buildDirectory)
        assertEquals("", state.additionalConfiguration)
        assertEquals("", state.androidNDKDirectory)
    }

    @Test
    fun `existing profiles are not migrated again and stale active name falls back`() {
        val settings = XMakeSettings()
        settings.loadState(XMakeSettings.State(
            profiles = mutableListOf(
                XMakeProfile(name = "Host"),
                XMakeProfile(name = "Cross", platform = "cross", toolchain = "muslcc"),
            ),
            activeProfileName = "Deleted",
            // Legacy leftovers must NOT spawn another profile once profiles exist.
            platform = "mingw",
        ))

        val state = settings.state
        assertEquals(listOf("Host", "Cross"), state.profiles.map { it.name })
        assertEquals("Host", state.activeProfileName)
        assertEquals("Host", settings.activeProfile.name)
    }

    @Test
    fun `active profile is resolved by name`() {
        val settings = XMakeSettings()
        settings.loadState(XMakeSettings.State(
            profiles = mutableListOf(XMakeProfile(name = "Host"), XMakeProfile(name = "Cross")),
            activeProfileName = "Cross",
        ))
        assertEquals("Cross", settings.activeProfile.name)
    }

    @Test
    fun `fresh project gets a default profile`() {
        val settings = XMakeSettings()
        settings.noStateLoaded()

        val state = settings.state
        assertEquals(1, state.profiles.size)
        assertEquals(XMakeSettings.DEFAULT_PROFILE_NAME, state.profiles.first().name)
        assertEquals(XMakeSettings.DEFAULT_PROFILE_NAME, state.activeProfileName)
        assertEquals("default", settings.activeProfile.platform)
    }

    @Test
    fun `profiles survive an XmlSerializer round-trip`() {
        val original = XMakeSettings.State(
            profiles = mutableListOf(
                XMakeProfile(
                    name = "Host",
                    platform = "windows",
                    architecture = "x64",
                    toolchain = "msvc",
                    buildDirectory = "build-host",
                    additionalConfiguration = "--vs_runtime=MT",
                ),
                XMakeProfile(name = "Android", platform = "android", androidNDKDirectory = "C:/ndk"),
            ),
            activeProfileName = "Android",
        )

        val element = XmlSerializer.serialize(original)
        val restored = XmlSerializer.deserialize(element, XMakeSettings.State::class.java)

        assertEquals(original.profiles, restored.profiles)
        assertEquals("Android", restored.activeProfileName)
        assertTrue(restored.profiles[1].androidNDKDirectory == "C:/ndk")
    }
}
