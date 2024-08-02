package io.xmake.project

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessNotCreatedException
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.ide.util.projectWizard.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ssh.config.unified.SshConfig
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.run.XMakeRunConfiguration
import io.xmake.run.XMakeRunConfigurationType
import io.xmake.utils.SystemUtils
import io.xmake.utils.execute.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File


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

        val dir = when(configurationData.toolkit!!.host.type) {
            LOCAL -> tmpdir
            WSL -> configurationData.remotePath
            SSH -> configurationData.remotePath
        }

        Log.debug("dir: $dir")

        val command = listOf(
            SystemUtils.xmakeProgram,
            "create",
            "-P",
            dir,
            "-l",
            configurationData.languagesModel,
            "-t",
            configurationData.kindsModel
        )

        val commandLine: GeneralCommandLine = GeneralCommandLine(command)
//            .withWorkDirectory(workDir)
            .withCharset(Charsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        val results = try {
            val (result, exitCode) = runBlocking(Dispatchers.IO) {
                return@runBlocking runProcess(commandLine.createProcess(configurationData.toolkit!!))
            }
            result.getOrDefault("").split(Regex("\\s+"))
        } catch (e: ProcessNotCreatedException) {
            emptyList()
        }

        Log.info("results: $results")

        with(configurationData.toolkit!!) {
            when(host.type) {
                LOCAL -> {
                    val tmpFile = File(tmpdir)
                    if (tmpFile.exists()) {
                        tmpFile.copyRecursively(File(contentEntryPath), true)
                        tmpFile.deleteRecursively()
                    }
                }
                WSL -> {
                    syncProjectByWslSync(scope, rootModel.project, host.target as WSLDistribution, configurationData.remotePath!!, SyncDirection.UPSTREAM_TO_LOCAL)
                }
                SSH -> {
                    syncProjectBySftp(scope, rootModel.project, host.target as SshConfig, configurationData.remotePath!!, SyncDirection.UPSTREAM_TO_LOCAL)
                }
            }
        }
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
