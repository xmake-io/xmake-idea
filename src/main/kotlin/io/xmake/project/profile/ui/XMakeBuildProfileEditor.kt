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

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.componentsList.components.ScrollablePanel
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import io.xmake.icons.XMakeIcons
import io.xmake.project.profile.XMakeBuildProfile
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JComponent

internal class XMakeBuildProfileEditor(
    project: Project,
    initialProfile: XMakeBuildProfile,
    private val treeUpdater: Runnable,
) : NamedConfigurable<XMakeBuildProfile>(true, treeUpdater) {
    private val form = XMakeBuildProfileForm(project)
    private var appliedProfile = initialProfile.copy()
    private var profileName = appliedProfile.name

    init {
        form.reset(appliedProfile)
    }

    val profile: XMakeBuildProfile
        get() = appliedProfile.copy()

    override fun getDisplayName(): String = profileName

    override fun setDisplayName(value: String) {
        profileName = value
        treeUpdater.run()
    }

    override fun getBannerSlogan(): String = "XMake profile: $displayName"

    override fun getEditableObject(): XMakeBuildProfile = profile

    override fun getIcon(expanded: Boolean): Icon = XMakeIcons.XMAKE

    override fun createOptionsPanel(): JComponent {
        val content = ScrollablePanel(BorderLayout()).apply {
            add(form.component, BorderLayout.CENTER)
        }
        return object : JBScrollPane(content) {
            init {
                border = JBUI.Borders.empty()
            }

            override fun getMinimumSize(): Dimension = Dimension(
                content.minimumSize.width,
                super.minimumSize.height,
            )
        }
    }

    override fun isModified(): Boolean = form.createProfileSnapshot(profileName) != appliedProfile

    override fun apply() {
        profileName = profileName.trim()
        appliedProfile = form.createProfileSnapshot(profileName)
        treeUpdater.run()
    }

    override fun reset() {
        profileName = appliedProfile.name
        form.reset(appliedProfile)
    }

    override fun disposeUIResources() {
        Disposer.dispose(form)
        super.disposeUIResources()
    }
}
