package io.xmake.project

import com.intellij.ide.util.projectWizard.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import io.xmake.utils.SystemUtils
import io.xmake.utils.ioRunvInPool
import java.io.File


class XMakeModuleBuilder : ModuleBuilder() {
    var configurationData: XMakeConfigData? = null


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
        ioRunvInPool(
            listOf(
                SystemUtils.xmakeProgram,
                "create",
                "-P",
                tmpdir,
                "-l",
                configurationData?.languagesModel.toString(),
                "-t",
                configurationData?.kindsModel.toString()
            )
        )
        val tmpFile = File(tmpdir)
        if (tmpFile.exists()) {
            tmpFile.copyRecursively(File(contentEntryPath), true)
            tmpFile.deleteRecursively()
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
