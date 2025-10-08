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