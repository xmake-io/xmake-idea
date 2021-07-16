package org.tboox.xmake.project

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleConfigurationEditor
//import com.intellij.openapi.roots.ui.configuration.DefaultModuleConfigurationEditorFactory
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState

class XMakeModuleConfigurationEditorProvider : ModuleConfigurationEditorProvider {

    override fun createEditors(moduleConfigurationState: ModuleConfigurationState): Array<ModuleConfigurationEditor> {

        var editors = arrayOf<ModuleConfigurationEditor>()
//        val factory = DefaultModuleConfigurationEditorFactory.getInstance()

        /*
        editors += factory.createModuleContentRootsEditor(moduleConfigurationState)
        editors += factory.createClasspathEditor(moduleConfigurationState)
        */
        return editors
    }

    companion object {
        private val Log = Logger.getInstance(XMakeModuleConfigurationEditorProvider::class.java.getName())
    }
}
