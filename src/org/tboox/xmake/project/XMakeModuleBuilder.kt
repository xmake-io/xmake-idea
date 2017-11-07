package org.tboox.xmake.project

import com.intellij.ide.util.projectWizard.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import org.tboox.xmake.utils.SystemUtils

import javax.swing.DefaultComboBoxModel
import java.io.File

class XMakeModuleBuilder : ModuleBuilder() {

    // the module kinds
    val kindsModel = DefaultComboBoxModel<String>().apply {
        addElement("Console")
        addElement("Static Library")
        addElement("Shared Library")
    }

    // the module languages
    val languagesModel = DefaultComboBoxModel<String>().apply {
        addElement("C")
        addElement("C++")
        addElement("Rust")
        addElement("Dlang")
        addElement("Go")
        addElement("Swift")
        addElement("Objc")
        addElement("Objc++")
    }

    // get language
    val language : String
        get() = languagesModel.selectedItem.toString().toLowerCase()

    // get template
    val template : String
        get() = when (kindsModel.selectedItem.toString()) {
            "Console" -> "console"
            "Static Library" -> "static"
            "Shared Library" -> "shared"
            else -> "console"
        }


    override fun setupRootModel(rootModel: ModifiableRootModel) {

        // get content entry path
        val contentEntryPath = contentEntryPath ?: return

        // get content entry
        val contentEntry = doAddContentEntry(rootModel) ?: return

        // add source root
        val sourceRoot = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(contentEntryPath))!!
        contentEntry.addSourceFolder(sourceRoot, false)

        // create empty project
        SystemUtils.Runv(listOf(SystemUtils.xmakeProgram, "create", "-P", contentEntryPath, "-n", name, "-l", language, "-t", template))
    }

    override fun getModuleType(): ModuleType<*> {
        return XMakeModuleType.instance
    }

    override fun modifyProjectTypeStep(settingsStep: SettingsStep): ModuleWizardStep? {
        return XMakeSdkSettingsStep(settingsStep, this)
    }

    companion object {
        private val Log = Logger.getInstance(XMakeModuleBuilder::class.java.getName())
    }
}
