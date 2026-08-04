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
 * @file        XMakeProfilesPanel.kt
 *
 */
package io.xmake.project

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.xmakeActiveToolkit
import io.xmake.shared.XMakeReconfigure
import io.xmake.shared.xmakeConfigurationOrNull
import io.xmake.utils.info.XMakeInfo
import io.xmake.utils.info.XMakeInfoManager
import io.xmake.utils.info.xmakeInfo
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The "Profiles" section of Settings > Build > Xmake: a CMake-profiles-style master list +
 * detail form editing [XMakeProfile]s. All edits happen on a working copy and reach
 * [XMakeSettings] only in [apply] (standard Configurable semantics); if the values behind the
 * active profile changed on apply, the project is reconfigured (or marked as needing it),
 * mirroring the toolbar profile/mode switch behavior.
 */
class XMakeProfilesPanel(private val project: Project) : Disposable {

    private val listModel = CollectionListModel<XMakeProfile>()
    private val list = JBList(listModel)

    private val nameField = JBTextField()
    private val platformCombo = ComboBox<String>()
    private val archCombo = ComboBox<String>()
    private val toolchainCombo = ComboBox<String>()
    private val buildDirBrowser = DirectoryBrowser(project)
    private val ndkBrowser = DirectoryBrowser(project)
    private val extraArgsField = JBTextField()

    /** Name of the profile the toolbar chip should point at after [apply]. */
    private var workingActiveName: String = ""

    /** The working-copy profile currently shown in the detail form. */
    private var editedProfile: XMakeProfile? = null

    /** Guards the control listeners while the form is being (re)populated programmatically. */
    private var loading = false

    val component: JComponent

    init {
        list.cellRenderer = object : ColoredListCellRenderer<XMakeProfile>() {
            override fun customizeCellRenderer(
                list: JList<out XMakeProfile>,
                value: XMakeProfile?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                value ?: return
                append(value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (value.name == workingActiveName)
                    append(" (active)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                val summary = listOf(value.platform, value.architecture, value.toolchain)
                    .filter { it.isNotEmpty() && it != "default" }
                    .joinToString("/")
                if (summary.isNotEmpty())
                    append("  $summary", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
        list.selectionModel.addListSelectionListener { e ->
            if (e.valueIsAdjusting || loading) return@addListSelectionListener
            flushEditor()
            loadEditor(list.selectedValue)
        }

        val decorator = ToolbarDecorator.createDecorator(list)
            .setAddAction {
                flushEditor()
                val profile = XMakeProfile(name = uniqueName("Profile"))
                listModel.add(profile)
                list.setSelectedValue(profile, true)
            }
            .setRemoveAction {
                val selected = list.selectedValue ?: return@setRemoveAction
                val index = list.selectedIndex
                editedProfile = null
                listModel.remove(selected)
                if (workingActiveName == selected.name)
                    workingActiveName = listModel.items.firstOrNull()?.name ?: ""
                list.selectedIndex = index.coerceAtMost(listModel.size - 1)
            }
            .setRemoveActionUpdater { listModel.size > 1 }
            .addExtraAction(object : com.intellij.openapi.actionSystem.AnAction(
                "Duplicate", "Duplicate the selected profile", AllIcons.Actions.Copy
            ) {
                override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.EDT

                override fun update(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    e.presentation.isEnabled = list.selectedValue != null
                }

                override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                    flushEditor()
                    val selected = list.selectedValue ?: return
                    val copy = selected.copy(name = uniqueName(selected.name))
                    listModel.add(copy)
                    list.setSelectedValue(copy, true)
                }
            })

        val listPanel = decorator.createPanel().apply {
            preferredSize = JBUI.size(180, 220)
        }

        val browseHost = project.xmakeActiveToolkit?.host ?: ToolkitHost()
        buildDirBrowser.addBrowserListenerByHostType(browseHost)
        ndkBrowser.addBrowserListenerByHostType(browseHost)

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("Name:", nameField)
            .addLabeledComponent("Platform:", platformCombo)
            .addLabeledComponent("Architecture:", archCombo)
            .addLabeledComponent("Toolchain:", toolchainCombo)
            .addLabeledComponent("Build directory:", buildDirBrowser)
            .addLabeledComponent("Android NDK:", ndkBrowser)
            .addLabeledComponent("Extra config args:", extraArgsField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
            .apply {
                border = JBUI.Borders.compound(
                    IdeBorderFactory.createBorder(),
                    JBUI.Borders.empty(8)
                )
            }

        component = JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
            add(listPanel, BorderLayout.WEST)
            add(form, BorderLayout.CENTER)
        }

        installListeners()
        reset()

        // Refresh the option lists whenever xmake re-probes the project (platforms/toolchains/...).
        project.messageBus.connect(this).subscribe(
            XMakeInfoManager.XMAKE_INFO_TOPIC,
            object : XMakeInfoManager.XMakeInfoListener {
                override fun onXMakeInfoUpdated(xmakeInfo: XMakeInfo) {
                    ApplicationManager.getApplication().invokeLater({
                        if (project.isDisposed) return@invokeLater
                        loadEditor(editedProfile)
                    }, project.disposed)
                }
            }
        )
    }

    private fun installListeners() {
        nameField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = nameChanged()
            override fun removeUpdate(e: DocumentEvent) = nameChanged()
            override fun changedUpdate(e: DocumentEvent) = nameChanged()

            private fun nameChanged() {
                if (loading) return
                val profile = editedProfile ?: return
                val newName = nameField.text
                if (workingActiveName == profile.name) workingActiveName = newName
                profile.name = newName
                list.repaint()
            }
        })
        platformCombo.addActionListener {
            if (loading) return@addActionListener
            editedProfile?.platform = selected(platformCombo, "default")
            repopulateArchitectures()
            updateNdkEnabled()
            list.repaint()
        }
        archCombo.addActionListener {
            if (loading) return@addActionListener
            editedProfile?.architecture = selected(archCombo, "default")
            list.repaint()
        }
        toolchainCombo.addActionListener {
            if (loading) return@addActionListener
            editedProfile?.toolchain = selected(toolchainCombo, "default")
            list.repaint()
        }
    }

    /** Push the detail form into the currently edited working-copy profile. */
    private fun flushEditor() {
        val profile = editedProfile ?: return
        profile.name = nameField.text
        profile.platform = selected(platformCombo, "default")
        profile.architecture = selected(archCombo, "default")
        profile.toolchain = selected(toolchainCombo, "default")
        profile.buildDirectory = buildDirBrowser.text
        profile.androidNDKDirectory = ndkBrowser.text
        profile.additionalConfiguration = extraArgsField.text
    }

    /** Populate the detail form from [profile] (and the freshly probed [XMakeInfo]). On the EDT. */
    private fun loadEditor(profile: XMakeProfile?) {
        editedProfile = profile
        loading = true
        try {
            val info = project.xmakeInfo
            nameField.text = profile?.name ?: ""
            setItems(platformCombo, listOf("default") + info.platforms, profile?.platform ?: "default")
            setItems(archCombo, architectureOptions(info, profile?.platform ?: "default"), profile?.architecture ?: "default")
            setItems(toolchainCombo, listOf("default") + info.toolchains.keys, profile?.toolchain ?: "default")
            buildDirBrowser.text = profile?.buildDirectory ?: ""
            ndkBrowser.text = profile?.androidNDKDirectory ?: ""
            extraArgsField.text = profile?.additionalConfiguration ?: ""
            val enabled = profile != null
            listOf<JComponent>(
                nameField, platformCombo, archCombo, toolchainCombo,
                buildDirBrowser, ndkBrowser, extraArgsField
            ).forEach { it.isEnabled = enabled }
            updateNdkEnabled()
        } finally {
            loading = false
        }
    }

    private fun repopulateArchitectures() {
        val wasLoading = loading
        loading = true
        try {
            val platform = selected(platformCombo, "default")
            setItems(archCombo, architectureOptions(project.xmakeInfo, platform), selected(archCombo, "default"))
            editedProfile?.architecture = selected(archCombo, "default")
        } finally {
            loading = wasLoading
        }
    }

    private fun updateNdkEnabled() {
        ndkBrowser.isEnabled = editedProfile != null && selected(platformCombo, "default") == "android"
    }

    private fun architectureOptions(info: XMakeInfo, platform: String): List<String> {
        val forPlatform = info.architectures[platform]
        val archs = forPlatform ?: info.architectures.values.flatten().distinct()
        return listOf("default") + archs
    }

    /** Unlike a plain repopulate, keeps [selected] selectable even if the probed options lack it. */
    private fun setItems(combo: ComboBox<String>, items: List<String>, selected: String) {
        val distinct = (items + listOf(selected).filter { it.isNotEmpty() }).distinct()
        combo.model = DefaultComboBoxModel(distinct.toTypedArray())
        combo.selectedItem = if (selected in distinct) selected else distinct.firstOrNull()
    }

    private fun selected(combo: ComboBox<String>, fallback: String): String =
        combo.selectedItem as? String ?: fallback

    private fun uniqueName(base: String): String {
        val names = listModel.items.map { it.name }.toSet()
        if (base !in names) return base
        var i = 2
        while ("$base ($i)" in names) i++
        return "$base ($i)"
    }

    fun isModified(): Boolean {
        flushEditor()
        val state = project.xmakeSettings.state
        return listModel.items != state.profiles || workingActiveName != state.activeProfileName
    }

    @Throws(ConfigurationException::class)
    fun apply() {
        flushEditor()
        val profiles = listModel.items
        if (profiles.any { it.name.isBlank() })
            throw ConfigurationException("Profile names must not be empty")
        if (profiles.map { it.name }.toSet().size != profiles.size)
            throw ConfigurationException("Profile names must be unique")

        val settings = project.xmakeSettings
        val activeBefore = settings.activeProfile.copy()
        settings.state.profiles = profiles.map { it.copy() }.toMutableList()
        settings.state.activeProfileName =
            if (profiles.any { it.name == workingActiveName }) workingActiveName else profiles.first().name
        workingActiveName = settings.state.activeProfileName

        // Same behavior as switching the profile/mode from the toolbar: if the effective active
        // configuration changed, reconfigure now or let the next build do it.
        if (settings.activeProfile != activeBefore) {
            if (settings.state.autoReloadConfigOnSwitch) {
                XMakeReconfigure.reconfigure(project)
            } else {
                project.xmakeConfigurationOrNull?.changed = true
            }
        }
    }

    fun reset() {
        val state = project.xmakeSettings.state
        loading = true
        try {
            editedProfile = null
            listModel.replaceAll(state.profiles.map { it.copy() })
            workingActiveName = state.activeProfileName
        } finally {
            loading = false
        }
        val active = listModel.items.firstOrNull { it.name == workingActiveName } ?: listModel.items.firstOrNull()
        if (active != null) list.setSelectedValue(active, true)
        // The selection listener does not fire when the index is unchanged — load explicitly.
        loadEditor(list.selectedValue)
    }

    override fun dispose() {}
}
