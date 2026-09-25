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

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.MasterDetails
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DetailsComponent
import javax.swing.JComponent
import io.xmake.run.target.activeOrSingleXMakeBuildProfile

/**
 * The Settings entry for project-owned XMake profiles.
 *
 * Implementing [MasterDetails] tells the Settings container that the page already
 * provides the standard master/details chrome; otherwise it adds another margin
 * around the editor and makes the page wider than the other pages.
 */
class XMakeBuildProfilesConfigurable(
    private val project: Project,
) : SearchableConfigurable, Configurable.NoScroll, MasterDetails {
    private var editor: XMakeBuildProfilesEditor? = null

    override fun createComponent() = editorOrCreate().component

    override fun isModified(): Boolean = editor?.isModified == true

    override fun apply() = editorOrCreate().applyChanges()

    override fun reset() {
        editor?.reset()
    }

    override fun disposeUIResources() {
        editor?.disposeUIResources()
        editor = null
    }

    override fun getDisplayName(): String = "XMake Profiles"

    override fun getId(): String = "XMakeBuildProfilesSettings"

    override fun initUi() = editorOrCreate().initUi()

    override fun getToolbar(): JComponent = editorOrCreate().toolbar

    override fun getMaster(): JComponent = editorOrCreate().master

    override fun getDetails(): DetailsComponent = editorOrCreate().detailsComponent

    private fun editorOrCreate(): XMakeBuildProfilesEditor = editor ?: XMakeBuildProfilesEditor(
        project,
        project.activeOrSingleXMakeBuildProfile?.id,
    ).also { editor = it }
}
