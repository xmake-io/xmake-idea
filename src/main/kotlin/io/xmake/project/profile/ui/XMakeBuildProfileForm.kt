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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.RawCommandLineEditor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.toJBEmptyBorder
import com.intellij.ui.layout.ComboBoxPredicate
import io.xmake.project.directory.resolveDefaultWorkingDirectory
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.XMakeBuildProfileOptions
import io.xmake.project.profile.queryXMakeBuildProfileOptions
import io.xmake.project.toolkit.ToolkitHost.Id
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ui.ToolkitComboBox
import io.xmake.project.toolkit.ui.ToolkitListItem
import io.xmake.utils.execute.SyncDirection
import io.xmake.utils.execute.transferProjectFiles
import io.xmake.utils.path.WorkingDirectoryResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

private data class OptionRequest(
    val profile: XMakeBuildProfile,
    val debounceMillis: Long,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class XMakeBuildProfileForm(
    private val project: Project,
) : Disposable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var baselineProfile = XMakeBuildProfile()
    private var selectedToolkit: Toolkit? = null
    private var workingDirectoryHostId: Id? = null
    private var isDefaultWorkingDirectoryPending = false
    private var profileOptions = XMakeBuildProfileOptions()
    private var loadedOptionsProfile: XMakeBuildProfile? = null
    private var optionToolkitId: String? = null
    private val optionRequests = Channel<OptionRequest>(Channel.CONFLATED)
    private val workingDirectoryRequests = Channel<Toolkit>(Channel.CONFLATED)
    private var isResetting = false
    private var isUpdatingOptions = false

    private val toolkitComboBox = ToolkitComboBox(project, ::selectedToolkit)
    private val platformComboBox = XMakeBuildProfileOptionComboBox()
    private val architectureComboBox = XMakeBuildProfileOptionComboBox()
    private val toolchainComboBox = XMakeBuildProfileOptionComboBox()
    private val buildModeComboBox = XMakeBuildProfileOptionComboBox()
    private val workingDirectory = DirectoryBrowser(
        project,
        browseTitle = "Working Directory",
        browseDescription = "Select the working directory",
    )
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

    val component: JComponent = panel {
        row("XMake toolkit:") {
            cell(toolkitComboBox).align(AlignX.FILL)
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
            row("Working directory:") {
                cell(workingDirectory).align(AlignX.FILL)
            }
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
        if (component.isShowing) onFormShown()
    }

    init {
        scope.launch {
            optionRequests.consumeAsFlow()
                .debounce { request -> request.debounceMillis }
                .transformLatest { request ->
                    val options = try {
                        project.queryXMakeBuildProfileOptions(request.profile)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.debug("Unable to load XMake build profile options", error)
                        null
                    }
                    emit(request to options)
                }
                .collect { (request, options) ->
                    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                        options?.let { applyProfileOptions(request, it) }
                    }
                }
        }
        scope.launch {
            workingDirectoryRequests.consumeAsFlow()
                .transformLatest { toolkit ->
                    val directory = try {
                        toolkit.resolveDefaultWorkingDirectory(project)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.debug("Unable to load the default XMake working directory", error)
                        null
                    }
                    emit(toolkit to directory)
                }
                .collect { (toolkit, directory) ->
                    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                        applyDefaultWorkingDirectory(toolkit, directory)
                    }
                }
        }
        Disposer.register(this, toolkitComboBox)
        toolkitComboBox.addSelectionListener { selectedToolkit ->
            if (!isResetting) onToolkitSelected(selectedToolkit)
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
        workingDirectory.textField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) {
                if (!isResetting) requestProfileOptions(PROFILE_OPTIONS_RELOAD_DELAY_MS)
            }
        })
        component.addHierarchyListener(profileOptionsVisibilityListener)
    }

    fun reset(profile: XMakeBuildProfile) {
        profileOptions = XMakeBuildProfileOptions()
        loadedOptionsProfile = null
        baselineProfile = profile.copy()
        isResetting = true
        try {
            selectedToolkit = profile.resolveToolkit(project)
            optionToolkitId = selectedToolkit?.id
            workingDirectoryHostId = selectedToolkit?.host?.id
            workingDirectory.text = profile.workingDirectory
            buildDirectory.text = profile.buildDirectory
            androidNdkDirectory.text = profile.androidNdkDirectory
            verbose.isSelected = profile.verbose
            configureArguments.text = profile.configureArguments

            platformComboBox.updateOptions(emptyList(), profile.platform)
            architectureComboBox.updateOptions(emptyList(), profile.architecture)
            toolchainComboBox.updateOptions(emptyList(), profile.toolchain)
            buildModeComboBox.updateOptions(
                listOf(XMakeBuildProfile.DEFAULT_BUILD_MODE, "debug"),
                profile.buildMode,
                fallback = XMakeBuildProfile.DEFAULT_BUILD_MODE,
            )
            toolkitComboBox.selectToolkit(selectedToolkit)
            rebindDirectoryBrowsers()
            isDefaultWorkingDirectoryPending = selectedToolkit != null && workingDirectory.text.isBlank()
        } finally {
            isResetting = false
        }
        if (component.isShowing) onFormShown()
    }

    fun createProfileSnapshot(name: String): XMakeBuildProfile = baselineProfile.copy(
        name = name,
        toolkitId = selectedToolkit?.id,
        platform = platformComboBox.selectedItem?.toString() ?: XMakeBuildProfile.USE_XMAKE_DEFAULT,
        architecture = architectureComboBox.selectedItem?.toString() ?: XMakeBuildProfile.USE_XMAKE_DEFAULT,
        toolchain = toolchainComboBox.selectedItem?.toString() ?: XMakeBuildProfile.USE_XMAKE_DEFAULT,
        buildMode = buildModeComboBox.selectedItem?.toString() ?: XMakeBuildProfile.DEFAULT_BUILD_MODE,
        workingDirectory = workingDirectory.text,
        buildDirectory = buildDirectory.text,
        androidNdkDirectory = androidNdkDirectory.text,
        verbose = verbose.isSelected,
        configureArguments = configureArguments.text,
    )

    override fun dispose() {
        component.removeHierarchyListener(profileOptionsVisibilityListener)
        scope.cancel()
    }

    private fun onToolkitSelected(selectedToolkit: Toolkit?) {
        rebindDirectoryBrowsers()
        val toolkitId = selectedToolkit?.id
        if (toolkitId == optionToolkitId) {
            if (selectedToolkit != null && workingDirectory.text.isBlank()) {
                requestDefaultWorkingDirectory(selectedToolkit)
            }
            return
        }
        optionToolkitId = toolkitId
        profileOptions = XMakeBuildProfileOptions()
        updateOptionModels()

        if (selectedToolkit == null) {
            workingDirectoryHostId = null
            isDefaultWorkingDirectoryPending = false
            return
        }

        val hostId = selectedToolkit.host.id
        if (workingDirectoryHostId != hostId) {
            workingDirectoryHostId = hostId
            workingDirectory.text = ""
        }

        if (workingDirectory.text.isBlank()) {
            requestDefaultWorkingDirectory(selectedToolkit)
        } else {
            requestProfileOptions()
        }
    }

    private fun rebindDirectoryBrowsers() {
        listOf(workingDirectory, buildDirectory, androidNdkDirectory).forEach { browser ->
            browser.setToolkit(selectedToolkit)
        }
    }

    private fun onFormShown() {
        val toolkit = selectedToolkit
        if (isDefaultWorkingDirectoryPending && toolkit != null) {
            isDefaultWorkingDirectoryPending = false
            requestDefaultWorkingDirectory(toolkit)
        }
        requestProfileOptions()
    }

    private fun requestProfileOptions(debounceMillis: Long = 0) {
        if (!component.isShowing) return

        val toolkit = selectedToolkit
        val requestedProfile = toolkit?.let(::profileForOptionQuery)
        if (requestedProfile == null) {
            profileOptions = XMakeBuildProfileOptions()
            updateOptionModels()
            return
        }
        if (loadedOptionsProfile == requestedProfile) return

        optionRequests.trySend(OptionRequest(requestedProfile, debounceMillis))
    }

    private fun applyProfileOptions(request: OptionRequest, options: XMakeBuildProfileOptions) {
        if (project.isDisposed || !component.isShowing) return
        if (profileForOptionQuery(selectedToolkit ?: return) != request.profile) return
        profileOptions = options
        loadedOptionsProfile = request.profile
        updateOptionModels()
    }

    private fun profileForOptionQuery(selectedToolkit: Toolkit): XMakeBuildProfile? {
        if (
            !selectedToolkit.isAvailable ||
            selectedToolkit.path.isBlank() ||
            selectedToolkit.requiresBackend && !selectedToolkit.host.hasBackend ||
            workingDirectory.text.isBlank()
        ) {
            return null
        }
        return createProfileSnapshot(baselineProfile.name)
    }

    private fun updateOptionModels() {
        isUpdatingOptions = true
        try {
            platformComboBox.updateOptions(
                profileOptions.platforms + profileOptions.architectures.keys,
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
                    .ifEmpty { listOf(XMakeBuildProfile.DEFAULT_BUILD_MODE, "debug") },
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
            baselineProfile.architecture in architectures -> baselineProfile.architecture
            architectures.isNotEmpty() -> architectures.first()
            else -> XMakeBuildProfile.USE_XMAKE_DEFAULT
        }
        architectureComboBox.updateOptions(architectures, selectedArchitecture)
    }

    private fun requestDefaultWorkingDirectory(toolkit: Toolkit) {
        if (component.isShowing) {
            workingDirectoryRequests.trySend(toolkit)
        } else {
            isDefaultWorkingDirectoryPending = true
        }
    }

    private fun applyDefaultWorkingDirectory(toolkit: Toolkit, directory: String?) {
        if (project.isDisposed || !component.isShowing) return
        if (selectedToolkit?.host?.id != toolkit.host.id) return
        if (workingDirectory.text.isNotBlank()) return
        if (directory.isNullOrBlank()) {
            isDefaultWorkingDirectoryPending = true
        } else {
            isDefaultWorkingDirectoryPending = false
            workingDirectory.text = directory
        }
    }

    private fun startFileTransfer(direction: SyncDirection) {
        val toolkit = selectedToolkit ?: return
        val directory = workingDirectory.text
        scope.launch {
            try {
                withBackgroundProgress(project, "Transfer XMake project files", cancellable = true) {
                    val resolvedDirectory = WorkingDirectoryResolver.resolve(project, directory, toolkit)
                    transferProjectFiles(project, toolkit, direction, resolvedDirectory)
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
        const val PROFILE_OPTIONS_RELOAD_DELAY_MS = 300L
        val Log = logger<XMakeBuildProfileForm>()
    }
}
