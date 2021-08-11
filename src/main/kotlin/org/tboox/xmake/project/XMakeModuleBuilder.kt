package org.tboox.xmake.project

import XMakeSdkSettingsStep
import com.intellij.ide.util.projectWizard.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.tboox.xmake.utils.SystemUtils


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

        // create empty project
        SystemUtils.Runv(
            listOf(
                SystemUtils.xmakeProgram,
                "create",
                "-P",
                contentEntryPath,
                "-l",
                configurationData?.languagesModel.toString(),
                "-t",
                configurationData?.kindsModel.toString()
            )
        )
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
