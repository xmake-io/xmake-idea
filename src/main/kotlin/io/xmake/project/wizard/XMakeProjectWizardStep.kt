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
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.shortenTextWithEllipsis
import com.intellij.openapi.ui.validation.*
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.project.stateStore
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.util.getTextWidth
import com.intellij.util.containers.map2Array
import io.xmake.project.directory.XMakeProjectDirectoryState
import io.xmake.project.directory.XMakeProjectDirectoryState.HostDirectory
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
import io.xmake.project.wizard.XMakeNewProjectWizardData.Companion.xmakeData
import io.xmake.run.XMakeRunConfigurationType
import io.xmake.run.target.XMakeBuildProfileExecutionTarget
import io.xmake.utils.execute.ProcessTimeoutException
import io.xmake.utils.execute.SyncDirection
import io.xmake.utils.execute.awaitBounded
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.transferProjectFiles
import java.io.File
import java.io.IOException
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import kotlin.io.path.Path
import kotlin.time.Duration.Companion.seconds

class XMakeProjectWizardStep(parent: NewProjectWizardBaseStep) :
    AbstractNewProjectWizardStep(parent),
    XMakeNewProjectWizardData {

    private val toolkitManager = ToolkitManager.getInstance()

    override val nameProperty: GraphProperty<String> = baseData!!.nameProperty
    override val pathProperty: GraphProperty<String> = baseData!!.pathProperty
    override val remotePathProperty: GraphProperty<String> = propertyGraph.lazyProperty { "" }
    override val languagesProperty: GraphProperty<String> =
        propertyGraph.lazyProperty { languagesModel.selectedItem?.toString().orEmpty() }
    override val kindsProperty: GraphProperty<String> =
        propertyGraph.lazyProperty { kindsModel.selectedItem?.toString().orEmpty() }
    override val toolkitProperty: GraphProperty<Toolkit?> = propertyGraph.lazyProperty {
        val registeredToolkits = toolkitManager.registeredToolkits(context.project)
        toolkitManager.defaultToolkitId
            ?.let { id -> registeredToolkits.firstOrNull { toolkit -> toolkit.id == id } }
            ?: registeredToolkits.firstOrNull()
    }
    private val requiresBackendProperty: GraphProperty<Boolean> =
        propertyGraph.lazyProperty { toolkit?.requiresBackend == true }

    override var name: String by nameProperty
    override var path: String by pathProperty
    override var remotePath: String by remotePathProperty
    override var language: String by languagesProperty
    override var kind: String by kindsProperty
    override var toolkit: Toolkit? by toolkitProperty
    private var requiresBackend by requiresBackendProperty

    private val kindOptions = listOf(
        "Console",
        "Static Library",
        "Shared Library",
    ).associateWith { it.substringBefore(' ').lowercase() }

    private val languageOptions = listOf(
        "C",
        "C++",
        "Rust",
        "Dlang",
        "Go",
        "Swift",
        "Objc",
        "Objc++",
    ).associateWith { it.lowercase() }

    // the module kinds
    private val kindsModel = DefaultComboBoxModel<String>().apply {
        kindOptions.keys.forEach { addElement(it) }
    }

    // the module languages
    private val languagesModel = DefaultComboBoxModel<String>().apply {
        languageOptions.keys.forEach { addElement(it) }
    }

    private val browser = DirectoryBrowser(context.project)
    private val toolkitComboBox = ToolkitComboBox(context.project, ::toolkit)

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
                cell(toolkitComboBox).applyToComponent {
                    addSelectionListener { toolkit ->
                        browser.setToolkit(toolkit)
                        requiresBackend = toolkit?.requiresBackend == true
                    }
                    browser.setToolkit(selectedToolkit)
                }
                    .validationRequestor(WHEN_PROPERTY_CHANGED(toolkitProperty))
                    .validationOnInput(REQUIRE_TOOLKIT_SELECTION.forToolkitComboBox())
                    .validationOnApply(REQUIRE_TOOLKIT_SELECTION.forToolkitComboBox())
                    .align(AlignX.FILL)

            }.bottomGap(BottomGap.SMALL)

            row("Module Language:") {
                comboBox(languagesModel)
                    .bindItem(languagesProperty)
                    .align(AlignX.FILL)
            }.bottomGap(BottomGap.SMALL)
            row("Module Type:") {
                comboBox(kindsModel)
                    .bindItem(kindsProperty)
                    .align(AlignX.FILL)
            }.bottomGap(BottomGap.SMALL)

            onApply {
                context.projectName = name
                context.setProjectFileDirectory(Path.of(path).resolve(name), false)
                toolkitManager.defaultToolkitId = toolkit?.id

                Log.info("wizard apply base data: ${baseData?.name}, ${baseData?.path}")
                Log.info(
                    "wizard apply xmake data: ${xmakeData?.name}," +
                            " ${xmakeData?.remotePath}," +
                            " ${xmakeData?.toolkit}" +
                            " ${languageOptions[(xmakeData?.language)]}" +
                            " ${kindOptions[xmakeData?.kind]}"
                )
            }
        }
    }

    override fun setupProject(project: Project) {
        if (context.isCreatingNewProject) {
            val selectedToolkit = toolkit
                ?: throw IllegalStateException("An XMake toolkit must be selected to create a project")
            val hostDirectory =
                if (!selectedToolkit.requiresBackend) File(contentEntryPath).path
                else remoteContentEntryPath

            val generationDirectory =
                if (!selectedToolkit.requiresBackend) File("$contentEntryPath.tmpdir").path
                else remoteContentEntryPath


            Log.info("contentEntry path: $contentEntryPath")
            Log.info("remote contentEntry path: $remoteContentEntryPath")
            Log.info("host directory: $hostDirectory")

            val language = languageOptions[xmakeData?.language]
                ?: throw IOException("Unsupported XMake project language: '${xmakeData?.language}'")
            val kind = kindOptions[xmakeData?.kind]
                ?: throw IOException("Unsupported XMake project kind: '${xmakeData?.kind}'")
            val commandLine = GeneralCommandLine(
                listOf(
                    selectedToolkit.path,
                    "create",
                    "-P",
                    generationDirectory,
                    "-l",
                    language,
                    "-t",
                    kind,
                )
            ).withCharset(Charsets.UTF_8)

            // setupProject runs on the EDT inside the wizard commit: the modal progress keeps
            // the IDE responsive and cancellable, and a failed creation aborts the setup via
            // IOException, which the wizard reports without opening a half-built project.
            runWithModalProgressBlocking(project, "Creating XMake project") {
                val result = try {
                    commandLine.createProcess(selectedToolkit).awaitBounded(XMAKE_CREATE_TIMEOUT)
                } catch (e: ProcessNotCreatedException) {
                    throw IOException("Failed to start xmake (${selectedToolkit.path}): ${e.message}", e)
                } catch (e: IOException) {
                    // Local and WSL starts throw a raw IOException from ProcessBuilder.
                    throw IOException("Failed to start xmake (${selectedToolkit.path}): ${e.message}", e)
                } catch (e: ProcessTimeoutException) {
                    throw IOException("xmake create timed out and was terminated", e)
                }
                val output = result.stdOut.toString(Charsets.UTF_8).trim()
                Log.info("XMake project creation output: $output")
                if (result.exitCode != 0) {
                    val diagnostics = (output + result.stdErr.toString(Charsets.UTF_8)).trim().take(2000)
                    throw IOException("xmake create failed (exit=${result.exitCode}): $diagnostics")
                }
            }

            with(selectedToolkit) {
                when (host.type) {
                    LOCAL -> {
                        val tempDirectory = File(generationDirectory)
                        if (tempDirectory.exists()) {
                            val copied = tempDirectory.copyRecursively(File(hostDirectory), true)
                            val deleted = tempDirectory.deleteRecursively()
                            if (!copied) {
                                throw IOException(
                                    "Failed to move the generated XMake project from $tempDirectory to $hostDirectory" +
                                            if (deleted) "" else " (leftover kept in $tempDirectory)"
                                )
                            }
                            if (!deleted) {
                                Log.warn("Failed to clean up the temporary XMake project directory $tempDirectory")
                            }
                        }
                    }

                    WSL, SSH -> {
                        runWithModalProgressBlocking(project, "Sync directory") {
                            transferProjectFiles(
                                project,
                                this@with,
                                SyncDirection.REMOTE_TO_LOCAL,
                                hostDirectory,
                                project.xmakeProjectDirectories.resolveLocalSyncDirectory(),
                            )
                        }
                    }
                }
            }

            val module = runWriteAction {
                with(ModuleManager.getInstance(project)) {
                    findModuleByName(name) ?: newModule(
                        project.stateStore.directoryStorePath!!.resolve(name),
                        (context.projectBuilder as? ModuleBuilder)?.moduleType?.id
                            ?: "NPW.XMakeProjectModuleBuilder"
                    )
                }
            }.also { Log.info("Created XMake project module: $it") }

            ModuleRootModificationUtil.updateModel(module) { model ->
                with(model.addContentEntry(VfsUtil.pathToUrl(contentEntryPath))) {
                    addSourceFolder(VfsUtil.fileToUrl(Path(contentEntryPath, "src").toFile()), false)
                    addExcludeFolder(VfsUtil.fileToUrl(project.stateStore.directoryStorePath!!.toFile()))
                    addExcludeFolder(VfsUtil.fileToUrl(Path(contentEntryPath, ".xmake").toFile()))
                }
            }

            val profile = XMakeBuildProfile(
                name = project.name,
                toolkitId = toolkit?.id,
            )
            project.xmakeProjectDirectories.replaceState(
                XMakeProjectDirectoryState(
                    localDirectory = File(contentEntryPath).path,
                    // Same asymmetry as importLegacyDirectory: WSL folds into the
                    // local directory, only SSH keeps a host-specific entry.
                    hostDirectories = if (selectedToolkit.host.type == SSH) {
                        mutableListOf(
                            HostDirectory(
                                selectedToolkit.host.id.canonical,
                                hostDirectory,
                            ),
                        )
                    } else {
                        mutableListOf()
                    },
                ),
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
    }

    init {
        Disposer.register(context.disposable, toolkitComboBox)
        data.putUserData(XMakeNewProjectWizardData.KEY, this)
    }

    companion object {

        private val XMAKE_CREATE_TIMEOUT = 120.seconds

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
