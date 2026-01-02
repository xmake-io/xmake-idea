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
 * @file        ToolkitManagerTest.kt
 *
 */
package io.xmake.project.toolkit

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ToolkitManagerTest {

    private lateinit var toolkitManager: ToolkitManager

    @Before
    fun setUp() {
        toolkitManager = ToolkitManager(CoroutineScope(Dispatchers.Default))
        toolkitManager.loadState(ToolkitManager.State())
    }

    @Test
    fun `test register and find toolkit`() {
        val localToolkit = Toolkit(
            name = "Local xmake",
            host = ToolkitHost(ToolkitHostType.LOCAL),
            path = "/usr/local/bin/xmake",
            version = "3.0.2"
        )

        toolkitManager.registerToolkit(localToolkit)

        val registeredToolkits = toolkitManager.getRegisteredToolkits()
        assertEquals("Should have one registered toolkit", 1, registeredToolkits.size)
        assertEquals("The registered toolkit should be the one we added", localToolkit, registeredToolkits.first())

        val foundToolkit = toolkitManager.findRegisteredToolkitById(localToolkit.id)
        assertNotNull("Should find the toolkit by its ID", foundToolkit)
        assertEquals("The found toolkit should be the same as the original", localToolkit, foundToolkit)
    }
}