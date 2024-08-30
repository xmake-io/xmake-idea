package io.xmake.project

import com.intellij.execution.RunManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import io.xmake.run.XMakeRunConfiguration
import io.xmake.run.XMakeRunConfigurationType
import io.xmake.utils.execute.SyncDirection
import io.xmake.utils.execute.createProcess
import io.xmake.utils.execute.runProcess
import io.xmake.utils.execute.transferFolderByToolkit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

@Deprecated("Please refer to the relevant content in folder io/xmake/project/wizard.")
class XMakeModuleBuilder : ModuleBuilder() {
    lateinit var configurationData: XMakeConfigData

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun isSuitableSdkType(sdkType: SdkTypeId?): Boolean = true


    override fun setupRootModel(rootModel: ModifiableRootModel) {

        // get content entry path
        val contentEntryPath = contentEntryPath ?: return

        // get content entry
        val contentEntry = doAddContentEntry(rootModel) ?: return

        // add source root
        val sourceRoot =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(contentEntryPath))!!
        contentEntry.addSourceFolder(sourceRoot, false)

        /* create empty project
         * @note we muse use ioRunv instead of Runv to read all output, otherwise it will wait forever on windows
         */
        val tmpdir = "$contentEntryPath.dir"

        val dir = if (!configurationData.toolkit!!.isOnRemote) tmpdir else configurationData.remotePath!!

        val workingDir =
            if (!configurationData.toolkit!!.isOnRemote) contentEntryPath else configurationData.remotePath!!

        Log.debug("dir: $dir")

        val command = listOf(
            configurationData.toolkit!!.path,
            "create",
            "-P",
            dir,
            "-l",
            configurationData.languagesModel,
            "-t",
            configurationData.kindsModel
        )

        val commandLine: GeneralCommandLine = GeneralCommandLine(command)
            .withWorkDirectory(workingDir)
            .withCharset(Charsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        val results = try {
            val (result, _) = runBlocking(Dispatchers.IO) {
                return@runBlocking runProcess(commandLine.createProcess(configurationData.toolkit!!))
            }
            result.getOrDefault("").split(Regex("\\s+"))
        } catch (e: ProcessNotCreatedException) {
            emptyList()
        }

        Log.info("results: $results")

        with(configurationData.toolkit!!) {
            if (!isOnRemote) {
                val tmpFile = File(tmpdir)
                if (tmpFile.exists()) {
                    tmpFile.copyRecursively(File(contentEntryPath), true)
                    tmpFile.deleteRecursively()
                }
            } else {
                transferFolderByToolkit(
                    rootModel.project,
                    this,
                    SyncDirection.UPSTREAM_TO_LOCAL,
                    directoryPath = configurationData.remotePath!!,
                    null
                )
            }
        }

        val runManager = RunManager.getInstance(rootModel.project)

        val configSettings = runManager.createConfiguration(rootModel.project.name, XMakeRunConfigurationType.getInstance().factory)
        runManager.addConfiguration(configSettings.apply {
            (configuration as XMakeRunConfiguration).apply {
                runToolkit = configurationData.toolkit
                runWorkingDir = workingDir
            }
        })
        runManager.selectedConfiguration = runManager.allSettings.first()
    }

    override fun getModuleType(): ModuleType<*> {
        return XMakeModuleType.instance
    }

    override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep =
        XMakeSdkSettingsStep(context).apply {
            Disposer.register(parentDisposable, this::disposeUIResources)
        }

    companion object {
        private val Log = Logger.getInstance(XMakeModuleBuilder::class.java.getName())
    }
}
