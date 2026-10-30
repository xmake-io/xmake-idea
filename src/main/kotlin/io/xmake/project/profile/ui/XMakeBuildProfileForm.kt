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
package io.xmake.project.profile.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.JBColor
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.toJBEmptyBorder
import com.intellij.ui.layout.ComboBoxPredicate
import io.xmake.project.directory.toolkitRunErrorMessage
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.XMakeBuildOptions
import io.xmake.project.toolkit.ToolkitInstallationKey
import io.xmake.utils.info.XMakeInfoManager
import io.xmake.project.toolkit.installationKey
import io.xmake.utils.runCancellable
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ui.ToolkitComboBox
import io.xmake.project.toolkit.ui.ToolkitListItem
import io.xmake.utils.execute.SyncDirection
import io.xmake.utils.execute.transferProjectFiles
import io.xmake.utils.ui.uiEdt
import io.xmake.utils.ui.loader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.ItemEvent
import javax.swing.JComponent

internal class XMakeBuildProfileForm(
    private val project: Project,
) : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val infoManager = XMakeInfoManager.getInstance(project)
    private var profile = XMakeBuildProfile()
    private var selectedToolkit: Toolkit? = null
    private var profileOptions = XMakeBuildOptions()
    private var loadedOptionsKey: ToolkitInstallationKey? = null
    private val toolkitValidator = ComponentValidator(this)
    private val loadOptions = loader<Toolkit>(scope) { toolkit ->
        withContext(uiEdt) { applyKnownRefusal(toolkit) }
        val options = runCancellable {
            infoManager.loadBuildOptions(profile, toolkit)
        }.getOrElse { error ->
            Log.debug("Unable to load XMake build options", error)
            return@loader
        }
        withContext(uiEdt) { applyProfileOptions(toolkit, options) }
    }
    private var isResetting = false
    private var isUpdatingOptions = false
    private val cachesConnection = project.messageBus.connect(this)

    private val toolkitComboBox = ToolkitComboBox(project)
    private val platformComboBox = XMakeBuildProfileOptionComboBox()
    private val architectureComboBox = XMakeBuildProfileOptionComboBox()
    private val toolchainComboBox = XMakeBuildProfileOptionComboBox()
    private val buildModeComboBox = XMakeBuildProfileOptionComboBox()
    private val buildDirectory = DirectoryBrowser(
        project,
        browseTitle = "Build Directory",
        browseDescription = "Select the build directory",
    )
    private val androidNdkDirectory = DirectoryBrowser(
        project,
        browseTitle = "Android NDK Directory",
        browseDescription = "Select the Android NDK directory",
    )
    private val verbose = JBCheckBox("Enable verbose output")
    private val configureArguments = RawCommandLineEditor()

    private val propertyGraph = PropertyGraph()
    private val minimumVersionLine = propertyGraph.property("")

    val component: JComponent = panel {
        row("XMake toolkit:") {
            cell(toolkitComboBox)
                .align(AlignX.FILL)
                .errorComment(minimumVersionLine)
        }

        row {
            label("Configuration:").align(AlignY.TOP)
            panel {
                row { label("Platform:") }
                row { cell(platformComboBox).align(AlignX.FILL) }
            }.resizableColumn()
            panel {
                row { label("Architecture:") }
                row { cell(architectureComboBox).align(AlignX.FILL) }
            }.resizableColumn()
            panel {
                row { label("Toolchain:") }
                row { cell(toolchainComboBox).align(AlignX.FILL) }
            }.resizableColumn()
        }.layout(RowLayout.PARENT_GRID)

        separator()

        row("Build mode:") {
            cell(buildModeComboBox).align(AlignX.FILL)
        }

        collapsibleGroup("Additional Configuration") {
            row("Build directory:") {
                cell(buildDirectory).align(AlignX.FILL)
            }
            row("Android NDK directory:") {
                cell(androidNdkDirectory).align(AlignX.FILL)
            }
            row("Additional options:") {
                cell(configureArguments).align(AlignX.FILL)
            }
            row {
                cell(verbose)
            }
        }

        row("Project files:") {
            button("Upload") {
                startFileTransfer(SyncDirection.LOCAL_TO_REMOTE)
            }
            button("Download") {
                startFileTransfer(SyncDirection.REMOTE_TO_LOCAL)
            }
        }.visibleIf(ComboBoxPredicate<ToolkitListItem>(toolkitComboBox) { item ->
            (item as? ToolkitListItem.Entry)?.toolkit?.requiresBackend == true
        })
    }.apply {
        border = IntelliJSpacingConfiguration().dialogUnscaledGaps.toJBEmptyBorder()
    }

    private val profileOptionsVisibilityListener = HierarchyListener { event ->
        if ((event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) == 0L) {
            return@HierarchyListener
        }
        if (component.isShowing) {
            refreshProjectScript()
            requestProfileOptions()
        }
    }

    /** An external edit reaches the cached options only after a VFS refresh; a real change
     *  re-queries through the topic, an unchanged file keeps the cache. */
    private fun refreshProjectScript() {
        val script = project.basePath
            ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
            ?.findChild("xmake.lua")
            ?: return
        LocalFileSystem.getInstance()
            .refreshFiles(mutableListOf(script), /* async = */ true, /* recursive = */ false) {}
    }

    init {
        Disposer.register(this, toolkitComboBox)
        toolkitValidator.installOn(toolkitComboBox)
        cachesConnection.subscribe(
            XMakeInfoManager.TOPIC,
            XMakeInfoManager.Listener {
                ApplicationManager.getApplication().invokeLater({
                    if (project.isDisposed) return@invokeLater
                    loadedOptionsKey = null
                    requestProfileOptions()
                }, ModalityState.any())
            },
        )
        toolkitComboBox.addSelectionListener { toolkit ->
            selectedToolkit = toolkit
            if (!isResetting) onToolkitSelected(toolkit)
        }
        platformComboBox.addItemListener { event ->
            if (
                event.stateChange == ItemEvent.SELECTED &&
                !isResetting &&
                !isUpdatingOptions
            ) {
                updateArchitectureOptions()
            }
        }
        component.addHierarchyListener(profileOptionsVisibilityListener)
    }

    fun reset(profile: XMakeBuildProfile) {
        profileOptions = XMakeBuildOptions()
        loadedOptionsKey = null
        this.profile = profile
        isResetting = true
        try {
            selectedToolkit = profile.resolveToolkit(project)
            buildDirectory.text = profile.buildDirectory
            androidNdkDirectory.text = profile.androidNdkDirectory
            verbose.isSelected = profile.verbose
            configureArguments.text = profile.configureArguments

            platformComboBox.updateOptions(emptyList(), profile.platform)
            architectureComboBox.updateOptions(emptyList(), profile.architecture)
            toolchainComboBox.updateOptions(emptyList(), profile.toolchain)
            buildModeComboBox.updateOptions(
                XMakeBuildProfile.FALLBACK_BUILD_MODES,
                profile.buildMode,
                fallback = XMakeBuildProfile.DEFAULT_BUILD_MODE,
            )
            toolkitComboBox.selectToolkit(selectedToolkit)
            rebindDirectoryBrowsers()
        } finally {
            isResetting = false
        }
        if (component.isShowing) requestProfileOptions()
    }

    fun createProfileSnapshot(name: String): XMakeBuildProfile = profile.copy(
        name = name,
        toolkitId = selectedToolkit?.id,
        platform = platformComboBox.selectedItem?.toString() ?: XMakeBuildProfile.USE_XMAKE_DEFAULT,
        architecture = architectureComboBox.selectedItem?.toString() ?: XMakeBuildProfile.USE_XMAKE_DEFAULT,
        toolchain = toolchainComboBox.selectedItem?.toString() ?: XMakeBuildProfile.USE_XMAKE_DEFAULT,
        buildMode = buildModeComboBox.selectedItem?.toString() ?: XMakeBuildProfile.DEFAULT_BUILD_MODE,
        buildDirectory = buildDirectory.text,
        androidNdkDirectory = androidNdkDirectory.text,
        verbose = verbose.isSelected,
        configureArguments = configureArguments.text,
    )

    /** Commits the toolkit selection; the profile editor calls this on the Settings apply path
     *  before taking the snapshot. */
    fun commitToolkitSelection(): Toolkit? = toolkitComboBox.commitSelection()

    override fun dispose() {
        component.removeHierarchyListener(profileOptionsVisibilityListener)
        scope.cancel()
    }

    private fun onToolkitSelected(selectedToolkit: Toolkit?) {
        rebindDirectoryBrowsers()
        if (selectedToolkit == null) {
            clearProfileOptions()
            return
        }
        if (selectedToolkit.installationKey() == loadedOptionsKey) return

        clearProfileOptions()
        requestProfileOptions()
    }

    private fun clearProfileOptions() {
        loadedOptionsKey = null
        profileOptions = XMakeBuildOptions()
        updateOptionModels()
    }

    private fun rebindDirectoryBrowsers() {
        listOf(buildDirectory, androidNdkDirectory).forEach { browser ->
            browser.setToolkit(selectedToolkit)
        }
    }

    private fun requestProfileOptions() {
        if (!component.isShowing) return

        val toolkit = selectedToolkit?.takeIf(::canQueryOptions)
        if (toolkit == null) {
            clearProfileOptions()
            return
        }
        if (toolkit.installationKey() == loadedOptionsKey) return

        loadOptions(toolkit)
    }

    // The known refusal applies at once; the loaded options replace it when they answer.
    private fun applyKnownRefusal(toolkit: Toolkit) {
        if ((selectedToolkit ?: return).installationKey() != toolkit.installationKey()) return
        profileOptions = XMakeBuildOptions(versionRefusal = infoManager.versionRefusal(toolkit))
        updateOptionModels()
    }

    private fun applyProfileOptions(toolkit: Toolkit, options: XMakeBuildOptions) {
        if (project.isDisposed || !component.isShowing) return
        if ((selectedToolkit ?: return).installationKey() != toolkit.installationKey()) return
        profileOptions = options
        loadedOptionsKey = toolkit.installationKey()
        updateOptionModels()
    }

    private fun canQueryOptions(toolkit: Toolkit): Boolean =
        // Only an installation with a usable host can run the query; the query itself validates
        // the project directory for a selection that is not registered yet.
        toolkit.isAvailable && toolkitRunErrorMessage(toolkit) == null

    private fun updateOptionModels() {
        val refusal = profileOptions.versionRefusal
        toolkitValidator.updateInfo(refusal?.let { ValidationInfo(it.message, toolkitComboBox) })
        minimumVersionLine.set(
            refusal?.minimumVersion
                ?.let { "The project requires xmake $it or newer" }
                .orEmpty(),
        )
        // No choice under a refused toolkit can build, so the dropdowns give up editing.
        platformComboBox.isEnabled = refusal == null
        architectureComboBox.isEnabled = refusal == null
        toolchainComboBox.isEnabled = refusal == null
        buildModeComboBox.isEnabled = refusal == null
        isUpdatingOptions = true
        try {
            platformComboBox.updateOptions(
                (profileOptions.platforms + profileOptions.architectures.keys).distinct().sorted(),
                platformComboBox.selectedItem?.toString(),
            )
            val platform = platformComboBox.selectedItem?.toString() ?: XMakeBuildProfile.USE_XMAKE_DEFAULT
            architectureComboBox.updateOptions(
                profileOptions.architectures[platform].orEmpty(),
                architectureComboBox.selectedItem?.toString(),
            )
            toolchainComboBox.updateOptions(
                profileOptions.toolchains.keys,
                toolchainComboBox.selectedItem?.toString(),
            )
            buildModeComboBox.updateOptions(
                profileOptions.buildModes
                    .map { mode -> mode.removePrefix("mode.") }
                    .ifEmpty { XMakeBuildProfile.FALLBACK_BUILD_MODES },
                buildModeComboBox.selectedItem?.toString(),
                fallback = XMakeBuildProfile.DEFAULT_BUILD_MODE,
            )
        } finally {
            isUpdatingOptions = false
        }
    }

    private fun updateArchitectureOptions() {
        val platform = platformComboBox.selectedItem?.toString() ?: XMakeBuildProfile.USE_XMAKE_DEFAULT
        val architectures = profileOptions.architectures[platform].orEmpty()
        val currentArchitecture = architectureComboBox.selectedItem?.toString()
        val selectedArchitecture = when {
            currentArchitecture in architectures -> currentArchitecture
            profile.architecture in architectures -> profile.architecture
            architectures.isNotEmpty() -> architectures.first()
            else -> XMakeBuildProfile.USE_XMAKE_DEFAULT
        }
        architectureComboBox.updateOptions(architectures, selectedArchitecture)
    }

    private fun startFileTransfer(direction: SyncDirection) {
        val toolkit = selectedToolkit ?: return
        scope.launch {
            try {
                withBackgroundProgress(project, "Transfer XMake project files", cancellable = true) {
                    val projectDirectory = project.xmakeProjectDirectories.resolveProjectDirectory(toolkit)
                    val localDirectory = project.xmakeProjectDirectories.resolveLocalSyncDirectory()
                    transferProjectFiles(project, toolkit, direction, projectDirectory, localDirectory)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.warn("Unable to transfer XMake project files", error)
                withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                    if (!project.isDisposed) {
                        Messages.showErrorDialog(
                            project,
                            error.message ?: "Unable to transfer XMake project files",
                            "XMake Project File Transfer",
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val Log = logger<XMakeBuildProfileForm>()
    }
}

/** Adds a red comment under a cell, bound to [text]; empty text hides the line. */
private fun Cell<*>.errorComment(text: ObservableMutableProperty<String>) {
    comment("", MAX_LINE_LENGTH_NO_WRAP)
    val line = comment!!.apply {
        foreground = JBColor.RED
        isVisible = false
    }
    text.afterChange { value ->
        line.isVisible = value.isNotEmpty()
        line.text = value
        line.parent?.revalidate()
    }
}
