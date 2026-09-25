package io.xmake.project.directory.ui

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import io.xmake.project.directory.XMakeProjectDirectoryState
import io.xmake.project.directory.XMakeProjectDirectoryState.HostDirectory
import io.xmake.project.directory.validateAbsoluteHostDirectory
import io.xmake.project.directory.validateLocalDirectory
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.project.toolkit.ToolkitManager
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class XMakeProjectDirectoryPanel(private val project: Project) {
    private var baselineState = project.xmakeProjectDirectories.getState()

    private val localDirectoryBrowser = DirectoryBrowser(
        project,
        browseTitle = "XMake Project Directory",
        browseDescription = "Select the directory containing the root xmake.lua",
    ).apply {
        setLocal()
    }

    private val hostDirectoryFieldsContainer = JPanel(BorderLayout())
    private var hostDirectoryFields = createHostDirectoryFields()

    val focusableComponent: JComponent
        get() = localDirectoryBrowser

    init {
        rebuildHostDirectoryFields()
    }

    /**
     * Adds the directory rows to [panel] so they share the Settings page grid with
     * the other labeled fields instead of using a separately-sized sub-panel.
     */
    fun attachTo(panel: Panel) {
        panel.row("Local directory:") {
            cell(localDirectoryBrowser)
                .align(AlignX.FILL)
                .comment("Directory containing root xmake.lua. Empty uses the IDE project root.")
        }
        panel.row {
            cell(hostDirectoryFieldsContainer).align(AlignX.FILL)
        }
    }

    fun refreshToolkits() {
        val editedDirectories = hostDirectoryFields.associate { field ->
            field.hostId to field.directoryBrowser.text
        }
        hostDirectoryFields = createHostDirectoryFields().map { field ->
            field.directoryBrowser.text = editedDirectories[field.hostId]
                ?: baselineState.hostDirectories
                    .firstOrNull { it.hostId == field.hostId }
                    ?.directory
                    .orEmpty()
            field
        }
        rebuildHostDirectoryFields()
    }

    private fun createHostDirectoryFields(): List<HostDirectoryField> =
        ToolkitManager.getInstance()
            .registeredToolkits(project)
            .distinctBy { toolkit -> toolkit.host.id.canonical }
            .filter { toolkit -> toolkit.host.type != ToolkitHostType.LOCAL }
            .map { toolkit -> HostDirectoryField(project, toolkit) }

    private fun rebuildHostDirectoryFields() {
        hostDirectoryFieldsContainer.removeAll()
        if (hostDirectoryFields.isEmpty()) {
            hostDirectoryFieldsContainer.isVisible = false
        } else {
            val hostPanel = panel {
                hostDirectoryFields.forEach { field ->
                    row("${field.displayName}:") {
                        cell(field.directoryBrowser).align(AlignX.FILL)
                    }
                }
                row {
                    comment(
                        "For WSL, empty resolves from the local project directory. SSH requires an absolute host path.",
                    )
                }
            }
            hostDirectoryFieldsContainer.add(hostPanel, BorderLayout.CENTER)
            hostDirectoryFieldsContainer.isVisible = true
        }
        hostDirectoryFieldsContainer.revalidate()
        hostDirectoryFieldsContainer.repaint()
    }

    fun reset() {
        baselineState = project.xmakeProjectDirectories.getState()
        localDirectoryBrowser.text = baselineState.localDirectory
        hostDirectoryFields.forEach { field ->
            field.directoryBrowser.text = baselineState.hostDirectories
                .firstOrNull { it.hostId == field.hostId }
                ?.directory.orEmpty()
        }
    }

    val isModified: Boolean
        get() = editedState() != baselineState

    @Throws(ConfigurationException::class)
    fun apply() {
        val state = editedState()
        requireValidState(state)
        project.xmakeProjectDirectories.replaceState(state)
        baselineState = state.copyState()
    }

    private fun editedState(): XMakeProjectDirectoryState {
        val editedHosts = hostDirectoryFields.mapNotNull { field ->
            field.directoryBrowser.text.trim()
                .takeUnless(String::isBlank)
                ?.let { directoryPath -> HostDirectory(field.hostId, directoryPath) }
        }
        val fieldHostIds = hostDirectoryFields.mapTo(mutableSetOf()) { it.hostId }
        // Keep entries for hosts that are not currently registered (an offline WSL distro or a
        // removed toolkit): dropping them would silently erase the configured directory.
        val hiddenHosts = baselineState.hostDirectories.filter { it.hostId !in fieldHostIds }
        return XMakeProjectDirectoryState(
            localDirectory = localDirectoryBrowser.text.trim(),
            hostDirectories = (editedHosts + hiddenHosts).toMutableList(),
        )
    }

    private fun requireValidState(state: XMakeProjectDirectoryState) {
        if (state.localDirectory.isNotBlank()) {
            try {
                validateLocalDirectory(project, state.localDirectory)
            } catch (error: RuntimeConfigurationError) {
                throw ConfigurationException(error.localizedMessage)
            }
        }

        val displayNames = hostDirectoryFields.associate { field ->
            field.hostId to field.displayName
        }
        state.hostDirectories.forEach { hostDirectory ->
            try {
                validateAbsoluteHostDirectory(hostDirectory.directory)
            } catch (error: RuntimeConfigurationError) {
                val hostName = displayNames[hostDirectory.hostId] ?: hostDirectory.hostId
                throw ConfigurationException("XMake $hostName: ${error.localizedMessage}")
            }
        }
    }

    private class HostDirectoryField(
        project: Project,
        toolkit: Toolkit,
    ) {
        val hostId = toolkit.host.id.canonical
        val displayName = "${toolkit.host.type}: ${toolkit.host.displayName}"
        val directoryBrowser = DirectoryBrowser(
            project,
            browseTitle = "XMake Project Directory",
            browseDescription = "Select the project directory on ${toolkit.host.displayName}",
        ).apply {
            setToolkit(toolkit)
        }
    }

}
