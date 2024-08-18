package io.xmake.project.wizard

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Base.logLocationChanged
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardBaseData
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
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.getCanonicalPath
import com.intellij.openapi.ui.shortenTextWithEllipsis
import com.intellij.openapi.ui.validation.*
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.project.stateStore
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ui.UIBundle
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.util.getTextWidth
import com.intellij.util.containers.map2Array
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.project.toolkit.ui.ToolkitComboBox
import io.xmake.project.toolkit.ui.ToolkitComboBox.Companion.CHECK_NON_EMPTY_TOOLKIT
import io.xmake.project.toolkit.ui.ToolkitComboBox.Companion.forToolkitComboBox
import io.xmake.project.wizard.XMakeNewProjectWizardData.Companion.xmakeData
import io.xmake.run.XMakeRunConfiguration
import io.xmake.run.XMakeRunConfigurationType
import io.xmake.utils.execute.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import kotlin.io.path.Path

class XMakeProjectWizardStep(parent: NewProjectWizardBaseStep) :
    AbstractNewProjectWizardStep(parent),
    NewProjectWizardBaseData by parent.baseData!!,
    XMakeNewProjectWizardData {

    private val toolkitManager = ToolkitManager.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    override val remotePathProperty: GraphProperty<String> = propertyGraph.lazyProperty { "" }
    override val languagesProperty: GraphProperty<String> =
        propertyGraph.lazyProperty { languagesModel.selectedItem.toString() }
    override val kindsProperty: GraphProperty<String> =
        propertyGraph.lazyProperty { kindsModel.selectedItem.toString() }
    override val toolkitProperty: GraphProperty<Toolkit?> = propertyGraph.lazyProperty {
        toolkitManager.state.lastSelectedToolkitId?.let { toolkitManager.findRegisteredToolkitById(it) }
    }
    private val isOnRemoteProperty: GraphProperty<Boolean> =
        propertyGraph.lazyProperty { toolkit?.isOnRemote == true }

    override var remotePath: String by remotePathProperty
    override var language: String by languagesProperty
    override var kind: String by kindsProperty
    override var toolkit: Toolkit? by toolkitProperty
    private var isOnRemote by isOnRemoteProperty

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
    private val toolkitComboBox = ToolkitComboBox(::toolkit)

    @Suppress("UnstableApiUsage")
    override fun setupUI(builder: Panel) {
        val locationProperty = remotePathProperty.joinCanonicalPath(nameProperty)
        with(builder) {

            row("Remote Directory") {
                cell(browser)
                    .bindText(remotePathProperty.toUiPathProperty())
                    .align(AlignX.FILL)
                    .trimmedTextValidation(*validationsIf(CHECK_NON_EMPTY, CHECK_DIRECTORY) { !isOnRemote })
                    .whenTextChangedFromUi { logLocationChanged() }
                    .remoteLocationComment(context, locationProperty)
            }.enabledIf(isOnRemoteProperty).visibleIf(isOnRemoteProperty).bottomGap(BottomGap.SMALL)

            row("XMake Toolkit") {
                cell(toolkitComboBox).applyToComponent {
                    addToolkitChangedListener { toolkit ->
                        browser.removeBrowserAllListener()
                        toolkit?.let {
                            browser.addBrowserListenerByToolkit(it)
                        }
                        isOnRemote = toolkit?.isOnRemote == true
                    }
                    activatedToolkit?.let { browser.addBrowserListenerByToolkit(it) }
                }
                    .validationRequestor(WHEN_PROPERTY_CHANGED(toolkitProperty))
                    .validation(CHECK_NON_EMPTY_TOOLKIT.forToolkitComboBox())
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
                toolkitManager.state.lastSelectedToolkitId = toolkit?.id

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
            val workingDirectory = when (toolkit!!.host.type) {
                LOCAL -> File(contentEntryPath).path
                WSL, SSH -> remoteContentEntryPath
            }
            val generateDirectory = when (toolkit!!.host.type) {
                LOCAL -> File("$contentEntryPath.tmpdir").path
                WSL, SSH -> remoteContentEntryPath
            }

            Log.info("contentEntry path: $contentEntryPath")
            Log.info("remote contentEntry path: $remoteContentEntryPath")
            Log.info("working directory: $workingDirectory")

            val command = listOf(
                "xmake",
                "create",
                "-P",
                generateDirectory,
                "-l",
                languageOptions[(xmakeData?.language)],
                "-t",
                kindOptions[xmakeData?.kind]
            )
            val commandLine: GeneralCommandLine = GeneralCommandLine(command)
//                .withWorkDirectory(workingDirectory)
                .withCharset(Charsets.UTF_8)

            val results = try {
                val (result, exitCode) = runBlocking(Dispatchers.IO) {
                    return@runBlocking runProcess(commandLine.createProcess(toolkit!!))
                }
                result.getOrDefault("").split(Regex("\\s+"))
            } catch (e: ProcessNotCreatedException) {
                println(e.message)
                emptyList()
            }

            Log.info("results: $results")

            with(toolkit!!) {
                when (host.type) {
                    LOCAL -> {
                        val tempDirectory = File(generateDirectory)
                        if (tempDirectory.exists()) {
                            tempDirectory.copyRecursively(File(workingDirectory), true)
                            tempDirectory.deleteRecursively()
                        }
                    }

                    WSL -> {
                        syncProjectByWslSync(
                            scope,
                            project,
                            host.target as WSLDistribution,
                            workingDirectory,
                            SyncDirection.UPSTREAM_TO_LOCAL
                        )
                    }

                    SSH -> {
                        syncProjectBySftp(
                            scope,
                            project,
                            host.target as SshConfig,
                            workingDirectory,
                            SyncDirection.UPSTREAM_TO_LOCAL
                        )
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
            }.also { println("module: $it") }

            ModuleRootModificationUtil.updateModel(module) { model ->
                with(model.addContentEntry(VfsUtil.pathToUrl(contentEntryPath))) {
                    addSourceFolder(VfsUtil.fileToUrl(Path(contentEntryPath, "src").toFile()), false)
                    addExcludeFolder(VfsUtil.fileToUrl(project.stateStore.directoryStorePath!!.toFile()))
                    addExcludeFolder(VfsUtil.fileToUrl(Path(contentEntryPath, ".xmake").toFile()))
                }
            }

            with(RunManager.getInstance(project)) {
                val configSettings = createConfiguration(project.name, XMakeRunConfigurationType.getInstance().factory)
                addConfiguration(configSettings.apply {
                    (configuration as XMakeRunConfiguration).apply {
                        runToolkit = toolkit
                        runWorkingDir = workingDirectory
                    }
                })
                selectedConfiguration = allSettings.first()
            }
        }
    }

    init {
        data.putUserData(XMakeNewProjectWizardData.KEY, this)
    }

    companion object {

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

        private fun <T> validationsIf(
            vararg validations: DialogValidation.WithParameter<T>,
            predicate: () -> Boolean,
        ): Array<DialogValidation.WithParameter<T>> {
            return validations.map2Array { it.transformResult { if (predicate()) withOKEnabled() else this } }
        }

        private val Log = logger<XMakeProjectWizardStep>()
    }

}