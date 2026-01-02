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
 * @file        XMakeModuleConfigurationEditorProvider.kt
 *
 */
package io.xmake.project

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
