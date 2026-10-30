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
 * @file        XMakeProjectWizardStep.kt
 *
 */
package io.xmake.project.wizard

import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.shortenTextWithEllipsis
import com.intellij.openapi.ui.validation.*
import com.intellij.project.stateStore
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.util.getTextWidth
import com.intellij.util.containers.map2Array
import io.xmake.project.directory.XMakeProjectDirectoryState.HostDirectory
import io.xmake.project.directory.configureInitialXMakeProjectLayout
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.project.profile.xmakeBuildProfiles
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.project.toolkit.ui.ToolkitComboBox
import io.xmake.project.toolkit.ui.ToolkitComboBox.Companion.REQUIRE_TOOLKIT_SELECTION
import io.xmake.project.toolkit.ui.ToolkitComboBox.Companion.forToolkitComboBox
import io.xmake.run.XMakeRunConfigurationType
import io.xmake.run.target.XMakeBuildProfileExecutionTarget
import io.xmake.utils.cli.xmake
import io.xmake.utils.execute.ProcessTimeoutException
import io.xmake.utils.execute.SyncDirection
import io.xmake.utils.execute.failureDetail
import io.xmake.utils.execute.transferProjectFiles
import io.xmake.utils.info.XMakeProjectTemplates
import io.xmake.utils.info.probeXMakeProjectTemplates
import io.xmake.utils.ui.uiEdt
import io.xmake.utils.ui.loader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import kotlin.time.Duration.Companion.seconds

class XMakeProjectWizardStep(parent: NewProjectWizardBaseStep) :
    AbstractNewProjectWizardStep(parent),
    XMakeNewProjectWizardData {

    private val toolkitManager = ToolkitManager.getInstance()

    override val nameProperty: GraphProperty<String> = baseData!!.nameProperty
    override val pathProperty: GraphProperty<String> = baseData!!.pathProperty
    override val remotePathProperty: GraphProperty<String> = propertyGraph.lazyProperty { "" }
    override val languageProperty: GraphProperty<String> =
        propertyGraph.lazyProperty { languageModel.selectedItem?.toString().orEmpty() }
    override val kindProperty: GraphProperty<String> =
        propertyGraph.lazyProperty { kindModel.selectedItem?.toString().orEmpty() }
    override val toolkitProperty: GraphProperty<Toolkit?> = propertyGraph.lazyProperty {
        toolkitManager.defaultRegisteredToolkit(context.project)
    }
    private val requiresBackendProperty: GraphProperty<Boolean> =
        propertyGraph.lazyProperty { toolkit?.requiresBackend == true }

    override var name: String by nameProperty
    override var path: String by pathProperty
    override var remotePath: String by remotePathProperty
    override var language: String by languageProperty
    override var kind: String by kindProperty
    override var toolkit: Toolkit? by toolkitProperty
    private var requiresBackend by requiresBackendProperty

    /** The templates behind the combo boxes. */
    private var projectTemplates: XMakeProjectTemplates = FALLBACK_TEMPLATES

    private val kindModel = DefaultComboBoxModel<String>().apply {
        FALLBACK_TEMPLATES.values.first().forEach(::addElement)
    }

    private val languageModel = DefaultComboBoxModel<String>().apply {
        FALLBACK_TEMPLATES.keys.forEach(::addElement)
    }

    private val browser = DirectoryBrowser(context.project)
    private val toolkitComboBox = ToolkitComboBox(context.project)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loadTemplates = loader<Toolkit>(scope) { toolkit ->
        val templates = probeXMakeProjectTemplates(toolkit, context.project, TEMPLATE_LISTING_TIMEOUT)
            ?: return@loader
        withContext(uiEdt) { applyProjectTemplates(templates) }
    }

    // Wired in class scope, not in an apply block on the combo box: JComponent.getToolkit()
    // gives the receiver a synthetic `toolkit` property that shadows this step's `toolkit`.
    init {
        toolkitComboBox.addSelectionListener(::applyToolkitSelection)
        toolkitComboBox.selectToolkit(toolkit)
        applyToolkitSelection(toolkitComboBox.selectedToolkit)
        languageProperty.afterChange { refreshKindModel() }
    }

    private fun applyToolkitSelection(selected: Toolkit?) {
        toolkit = selected
        browser.setToolkit(selected)
        requiresBackend = selected?.requiresBackend == true
        selected?.let(loadTemplates)
    }

    private fun applyProjectTemplates(templates: XMakeProjectTemplates) {
        if (templates == projectTemplates) return
        projectTemplates = templates
        val previousLanguage = languageModel.selectedItem as? String
        languageModel.removeAllElements()
        templates.keys.forEach(languageModel::addElement)
        languageModel.selectedItem = previousLanguage?.takeIf { it in templates } ?: templates.keys.firstOrNull()
        refreshKindModel()
    }

    private fun refreshKindModel() {
        val language = languageModel.selectedItem as? String ?: return
        val kinds = projectTemplates[language] ?: return
        val previousKind = kindModel.selectedItem as? String
        kindModel.removeAllElements()
        kinds.forEach(kindModel::addElement)
        kindModel.selectedItem = previousKind?.takeIf { it in kinds } ?: kinds.firstOrNull()
    }

    override fun setupUI(builder: Panel) {
        val locationProperty = remotePathProperty.joinCanonicalPath(nameProperty)
        val remoteDirectoryValidations = arrayOf(CHECK_NON_EMPTY, CHECK_DIRECTORY).map2Array { validation ->
            validation.transformResult { if (!requiresBackend) withOKEnabled() else this }
        }
        with(builder) {

            row("Remote Directory") {
                cell(browser)
                    .bindText(remotePathProperty.toUiPathProperty())
                    .align(AlignX.FILL)
                    .trimmedTextValidation(*remoteDirectoryValidations)
                    .remoteLocationComment(context, locationProperty)
            }.enabledIf(requiresBackendProperty).visibleIf(requiresBackendProperty).bottomGap(BottomGap.SMALL)

            row("XMake Toolkit") {
                cell(toolkitComboBox)
                    .validationRequestor(WHEN_PROPERTY_CHANGED(toolkitProperty))
                    .validationOnInput(REQUIRE_TOOLKIT_SELECTION.forToolkitComboBox())
                    .validationOnApply(REQUIRE_TOOLKIT_SELECTION.forToolkitComboBox())
                    .align(AlignX.FILL)

            }.bottomGap(BottomGap.SMALL)

            row("Module Language:") {
                comboBox(languageModel)
                    .bindItem(languageProperty)
                    .align(AlignX.FILL)
            }.bottomGap(BottomGap.SMALL)
            row("Module Type:") {
                comboBox(kindModel)
                    .bindItem(kindProperty)
                    .align(AlignX.FILL)
            }.bottomGap(BottomGap.SMALL)

            onApply {
                context.projectName = name
                context.setProjectFileDirectory(Path.of(path).resolve(name), false)
                toolkitManager.defaultToolkitId = toolkitComboBox.commitSelection()?.id

                Log.info("wizard apply base data: ${baseData?.name}, ${baseData?.path}")
                Log.info("wizard apply xmake data: $name, $remotePath, $toolkit, $language, $kind")
            }
        }
    }

    override fun setupProject(project: Project) {
        if (!context.isCreatingNewProject) return

        val selectedToolkit = registerSelectedToolkit()
        val directories = resolveDirectories(selectedToolkit)
        createProject(project, selectedToolkit, directories)
        copyOrSyncProject(project, selectedToolkit, directories)
        val module = createModule(project)
        configureProject(project, selectedToolkit, module, directories)
    }

    // The wizard's listener order is not guaranteed; registering here is the main guarantee.
    private fun registerSelectedToolkit(): Toolkit {
        val current = toolkit ?: throw IllegalStateException("An available XMake toolkit must be selected to create a project")
        return toolkitManager.register(current.id, context.project)
            ?: throw IllegalStateException(
                "The selected XMake toolkit ${current.name} (${current.path}) could not be registered. " +
                        "Choose another toolkit or fix its host connection.",
            )
    }

    private fun resolveDirectories(toolkit: Toolkit): WizardDirectories {
        val localDirectory = File(contentEntryPath).path
        return if (toolkit.requiresBackend) {
            WizardDirectories(hostDirectory = remoteContentEntryPath, tempDirectory = null)
        } else {
            WizardDirectories(localDirectory, localDirectory + TEMP_DIRECTORY_SUFFIX)
        }
    }

    private fun createProject(project: Project, toolkit: Toolkit, directories: WizardDirectories) {
        val commandLine = xmake(toolkit)
            .create(directories.tempDirectory ?: directories.hostDirectory, language, kind)

        val tempDirectory = directories.tempDirectory
        if (tempDirectory != null) {
            val leftover = File(tempDirectory)
            if (leftover.exists() && !leftover.deleteRecursively()) {
                throw IOException(
                    "Failed to remove the leftover temp directory $tempDirectory from an earlier attempt",
                )
            }
            // xmake create refuses a non-empty directory; the copy step would
            // overwrite same-named files.
            if (File(directories.hostDirectory).list()?.isNotEmpty() == true) {
                throw IOException(
                    "${directories.hostDirectory} is not empty; pick an empty directory or remove its contents first",
                )
            }
        }

        Log.info("contentEntry path: $contentEntryPath")
        Log.info("remote contentEntry path: $remoteContentEntryPath")
        Log.info("host directory: ${directories.hostDirectory}")

        // setupProject runs on the EDT inside the wizard commit; a failed creation aborts
        // via IOException, which the wizard reports without opening a half-built project.
        runWithModalProgressBlocking(project, "Creating XMake project") {
            try {
                val result = try {
                    commandLine.execute(project, XMAKE_CREATE_TIMEOUT)
                } catch (e: ProcessNotCreatedException) {
                    throw IOException("Failed to start xmake (${toolkit.path}): ${e.message}", e)
                } catch (e: IOException) {
                    // Local and WSL starts throw a raw IOException from ProcessBuilder.
                    throw IOException("Failed to start xmake (${toolkit.path}): ${e.message}", e)
                } catch (e: ProcessTimeoutException) {
                    throw IOException("xmake create timed out and was terminated", e)
                }
                val output = result.stdOut.trim()
                Log.debug("Create output: $output")
                if (result.exitCode != 0) {
                    val diagnostics = failureDetail((output + result.stdErr).trim())
                    throw IOException("xmake create failed (exit=${result.exitCode}): $diagnostics")
                }
            } catch (error: IOException) {
                directories.tempDirectory?.let { temp ->
                    val tempDirectory = File(temp)
                    if (tempDirectory.exists() && !tempDirectory.deleteRecursively()) {
                        Log.warn("Failed to remove the partial temp directory $temp")
                    }
                }
                throw error
            }
        }
    }

    private fun copyOrSyncProject(project: Project, toolkit: Toolkit, directories: WizardDirectories) {
        when (toolkit.host.type) {
            LOCAL -> copyLocalProject(directories)
            WSL, SSH -> runWithModalProgressBlocking(project, "Sync directory") {
                transferProjectFiles(
                    project,
                    toolkit,
                    SyncDirection.REMOTE_TO_LOCAL,
                    directories.hostDirectory,
                    project.xmakeProjectDirectories.resolveLocalSyncDirectory(),
                )
            }
        }
    }

    private fun copyLocalProject(directories: WizardDirectories) {
        val tempDirectory = directories.tempDirectory?.let(::File) ?: return
        if (tempDirectory.exists()) {
            try {
                tempDirectory.copyRecursively(File(directories.hostDirectory), true)
            } catch (error: IOException) {
                // A leftover temp directory would block the next xmake create.
                val cleaned = tempDirectory.deleteRecursively()
                throw IOException(
                    "Failed to move the generated XMake project to ${directories.hostDirectory}, which may hold a partial copy" +
                            (if (cleaned) "" else "; delete the temp directory $tempDirectory before retrying"),
                    error,
                )
            }
            if (!tempDirectory.deleteRecursively()) {
                Log.warn("Failed to clean up the wizard temp directory $tempDirectory")
            }
        }
    }

    private fun createModule(project: Project): Module =
        runWriteAction {
            with(ModuleManager.getInstance(project)) {
                findModuleByName(name) ?: newModule(
                    project.stateStore.directoryStorePath!!.resolve(name),
                    (context.projectBuilder as? ModuleBuilder)?.moduleType?.id ?: FALLBACK_MODULE_TYPE_ID
                )
            }
        }.also { Log.info("Created XMake project module: $it") }

    private fun configureProject(
        project: Project,
        toolkit: Toolkit,
        module: Module,
        directories: WizardDirectories,
    ) {
        configureInitialXMakeProjectLayout(project, Path.of(contentEntryPath), module)
        if (toolkit.host.type == SSH) {
            // Same asymmetry as importLegacyDirectory: WSL folds into the local directory,
            // only SSH keeps a host-specific entry.
            project.xmakeProjectDirectories.replaceState(
                project.xmakeProjectDirectories.state.copy(
                    hostDirectories = mutableListOf(HostDirectory(toolkit.host.id.canonical, directories.hostDirectory)),
                ),
            )
        }

        val profile = XMakeBuildProfile(
            name = project.name,
            toolkitId = toolkit.id,
        )
        project.xmakeBuildProfiles.replaceProfiles(listOf(profile))
        with(RunManager.getInstance(project)) {
            val configSettings = createConfiguration(
                project.name,
                XMakeRunConfigurationType.getInstance().factory,
            )
            addConfiguration(configSettings)
            selectedConfiguration = configSettings
        }
        ExecutionTargetManager.setActiveTarget(
            project,
            XMakeBuildProfileExecutionTarget(project, profile),
        )
    }

    /** The project's final location, and the temp sibling xmake create writes into first
     *  (null when a backend toolkit generates straight into the final location). */
    private data class WizardDirectories(val hostDirectory: String, val tempDirectory: String?)

    init {
        Disposer.register(context.disposable, toolkitComboBox)
        Disposer.register(context.disposable) { scope.cancel() }
    }

    companion object {

        /** The built-in templates shown before the live listing answers, and kept when it fails. */
        private val FALLBACK_TEMPLATES: XMakeProjectTemplates = listOf(
            "c", "c++", "rust", "dlang", "go", "swift", "objc", "objc++",
        ).associateWith { listOf("console", "static", "shared") }

        private val XMAKE_CREATE_TIMEOUT = 120.seconds

        private val TEMPLATE_LISTING_TIMEOUT = 30.seconds

        // The welcome-screen path supplies no projectBuilder, and plugin.xml registers a
        // moduleBuilder but no moduleType; this id only has to stay stable and unique.
        private const val FALLBACK_MODULE_TYPE_ID = "NPW.XMakeProjectModuleBuilder"

        private const val TEMP_DIRECTORY_SUFFIX = ".tmpdir"

        private const val LOCATION_COMMENT_RATIO = 0.9f // Less than 1.0

        private fun Cell<TextFieldWithBrowseButton>.remoteLocationComment(
            context: WizardContext,
            locationProperty: ObservableProperty<String>,
        ) {
            comment("", MAX_LINE_LENGTH_NO_WRAP)
            val comment = comment!!
            val widthProperty = component.widthProperty
            val commentProperty = operation(locationProperty, widthProperty) { path, width ->
                val isCreatingNewProjectInt = context.isCreatingNewProjectInt
                val commentWithEmptyPath =
                    UIBundle.message("label.project.wizard.new.project.path.description", isCreatingNewProjectInt, "")
                val commentWidthWithEmptyPath = comment.getTextWidth(commentWithEmptyPath)
                val maxPathWidth = (LOCATION_COMMENT_RATIO * width).toInt() - commentWidthWithEmptyPath
                val presentablePath = getCanonicalPath(path)
                val shortPresentablePath = shortenTextWithEllipsis(
                    text = presentablePath,
                    maxTextWidth = maxPathWidth,
                    getTextWidth = comment::getTextWidth,
                )
                UIBundle.message(
                    "label.project.wizard.new.project.path.description",
                    isCreatingNewProjectInt,
                    shortPresentablePath
                )
            }
            comment.bind(commentProperty)
        }
        private val Log = logger<XMakeProjectWizardStep>()
    }

}
