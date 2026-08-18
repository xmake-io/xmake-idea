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
 */
package io.xmake.project.profile.ui

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent

internal class XMakeBuildProfilesDialog(
    project: Project,
    preselectedProfileId: String?,
) : DialogWrapper(project) {
    private val editor = XMakeBuildProfilesEditor(project, preselectedProfileId)

    init {
        title = "XMake Build Profiles"
        init()
    }

    override fun createCenterPanel(): JComponent = editor.component

    override fun getPreferredFocusedComponent(): JComponent = editor.tree

    override fun getDimensionServiceKey(): String = DIMENSION_KEY

    override fun getInitialSize(): Dimension = JBUI.size(980, 660)

    override fun doValidate(): ValidationInfo? = editor.validateProfiles()

    override fun doOKAction() {
        try {
            editor.applyChanges()
        } catch (error: ConfigurationException) {
            setErrorText(error.localizedMessage)
            return
        }
        super.doOKAction()
    }

    override fun dispose() {
        editor.disposeUIResources()
        super.dispose()
    }

    private companion object {
        const val DIMENSION_KEY = "XMake.BuildProfilesDialog"
    }
}
