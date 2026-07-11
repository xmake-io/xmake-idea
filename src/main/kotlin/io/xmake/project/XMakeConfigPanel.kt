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
 * @file        XMakeConfigPanel.kt
 *
 */
package io.xmake.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import io.xmake.shared.XMakeReconfigure
import io.xmake.shared.xmakeConfigurationOrNull
import io.xmake.utils.info.XMakeInfo
import io.xmake.utils.info.XMakeInfoManager
import io.xmake.utils.info.xmakeInfo
import java.awt.FlowLayout
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The "XMake Config" tool-window panel: a project-level editor for the xmake configuration
 * ([XMakeSettings]) — build mode, platform, architecture, toolchain, build directory and extra
 * `xmake f` arguments — plus explicit Reconfigure / Generate compile_commands actions.
 *
 * It is decoupled from the selected run configuration, so it works with the native "Xmake Executable"
 * run config selected. Combo/checkbox changes optionally auto-reconfigure (shared with the toolbar
 * mode dropdown via [XMakeReconfigure]); free-text fields apply on focus-loss / the Reconfigure
 * button to avoid reconfiguring on every keystroke. The option lists refresh from [XMakeInfo] whenever
 * xmake re-probes the project (`XMAKE_INFO_TOPIC`).
 */
class XMakeConfigPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val modeCombo = ComboBox<String>()
    private val platformCombo = ComboBox<String>()
    private val archCombo = ComboBox<String>()
    private val toolchainCombo = ComboBox<String>()
    private val buildDirField = JBTextField()
    private val extraArgsField = JBTextField()
    private val verboseCheck = JBCheckBox("Verbose output")
    private val autoReconfigureCheck = JBCheckBox("Reconfigure automatically on change")
    private val autoCompDbCheck = JBCheckBox("Regenerate compile_commands.json after reconfigure")

    /** Guards the change listeners while we programmatically repopulate the controls in [reload]. */
    private var loading = false

    init {
        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("Build mode:", modeCombo)
            .addLabeledComponent("Platform:", platformCombo)
            .addLabeledComponent("Architecture:", archCombo)
            .addLabeledComponent("Toolchain:", toolchainCombo)
            .addLabeledComponent("Build directory:", buildDirField)
            .addLabeledComponent("Extra config args:", extraArgsField)
            .addComponent(verboseCheck)
            .addComponent(autoReconfigureCheck)
            .addComponent(autoCompDbCheck)
            .addComponent(buttonRow())
            .addComponentFillVertically(JPanel(), 0)
            .panel
        setContent(JBScrollPane(form).apply { border = JBUI.Borders.empty(8) })

        installListeners()
        reload()

        // Refresh the option lists whenever xmake re-probes the project (targets/platforms/etc.).
        project.messageBus.connect(this).subscribe(
            XMakeInfoManager.XMAKE_INFO_TOPIC,
            object : XMakeInfoManager.XMakeInfoListener {
                override fun onXMakeInfoUpdated(xmakeInfo: XMakeInfo) = reload()
            }
        )
    }

    private fun buttonRow(): JComponent {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        panel.add(JButton("Reconfigure").apply {
            addActionListener {
                applyTextFields()
                XMakeReconfigure.reconfigure(project)
            }
        })
        panel.add(JButton("Generate compile_commands.json").apply {
            border = JBUI.Borders.emptyLeft(8)
            addActionListener {
                applyTextFields()
                XMakeReconfigure.generateCompileCommands(project)
            }
        })
        return panel
    }

    private fun installListeners() {
        modeCombo.addActionListener {
            if (loading) return@addActionListener
            state().buildMode = selected(modeCombo, "release")
            maybeReconfigure()
        }
        platformCombo.addActionListener {
            if (loading) return@addActionListener
            state().platform = selected(platformCombo, "default")
            repopulateArchitectures()
            maybeReconfigure()
        }
        archCombo.addActionListener {
            if (loading) return@addActionListener
            state().architecture = selected(archCombo, "default")
            maybeReconfigure()
        }
        toolchainCombo.addActionListener {
            if (loading) return@addActionListener
            state().toolchain = selected(toolchainCombo, "default")
            maybeReconfigure()
        }
        verboseCheck.addActionListener {
            if (loading) return@addActionListener
            state().verbose = verboseCheck.isSelected
            maybeReconfigure()
        }
        autoReconfigureCheck.addActionListener {
            if (loading) return@addActionListener
            state().autoReloadConfigOnSwitch = autoReconfigureCheck.isSelected
        }
        autoCompDbCheck.addActionListener {
            if (loading) return@addActionListener
            state().autoUpdateCompileCommands = autoCompDbCheck.isSelected
        }
        // Text fields apply on focus-loss (not per keystroke); the Reconfigure button also applies them.
        val applyOnBlur = object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (loading) return
                if (applyTextFields()) maybeReconfigure()
            }
        }
        buildDirField.addFocusListener(applyOnBlur)
        extraArgsField.addFocusListener(applyOnBlur)
    }

    /** Push the free-text fields into state; returns true if anything actually changed. */
    private fun applyTextFields(): Boolean {
        val s = state()
        var changed = false
        if (s.buildDirectory != buildDirField.text) {
            s.buildDirectory = buildDirField.text
            changed = true
        }
        if (s.additionalConfiguration != extraArgsField.text) {
            s.additionalConfiguration = extraArgsField.text
            changed = true
        }
        return changed
    }

    private fun maybeReconfigure() {
        if (state().autoReloadConfigOnSwitch) {
            XMakeReconfigure.reconfigure(project)
        } else {
            project.xmakeConfigurationOrNull?.changed = true
        }
    }

    /** Repopulate every control from [XMakeSettings] + the freshly probed [XMakeInfo]. On the EDT. */
    private fun reload() {
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            loading = true
            try {
                val s = state()
                val info = project.xmakeInfo

                setItems(modeCombo, modeOptions(info), s.buildMode)
                setItems(platformCombo, listOf("default") + info.platforms, s.platform)
                setItems(archCombo, architectureOptions(info, s.platform), s.architecture)
                setItems(toolchainCombo, listOf("default") + info.toolchains.keys, s.toolchain)

                buildDirField.text = s.buildDirectory
                extraArgsField.text = s.additionalConfiguration
                verboseCheck.isSelected = s.verbose
                autoReconfigureCheck.isSelected = s.autoReloadConfigOnSwitch
                autoCompDbCheck.isSelected = s.autoUpdateCompileCommands
            } finally {
                loading = false
            }
        }, project.disposed)
    }

    private fun repopulateArchitectures() {
        val wasLoading = loading
        loading = true
        try {
            setItems(archCombo, architectureOptions(project.xmakeInfo, state().platform), state().architecture)
            state().architecture = selected(archCombo, "default")
        } finally {
            loading = wasLoading
        }
    }

    private fun modeOptions(info: XMakeInfo): List<String> =
        info.buildModes.map { it.removePrefix("mode.") }.ifEmpty { listOf("release", "debug") }

    private fun architectureOptions(info: XMakeInfo, platform: String): List<String> {
        val forPlatform = info.architectures[platform]
        val archs = forPlatform ?: info.architectures.values.flatten().distinct()
        return listOf("default") + archs
    }

    private fun setItems(combo: ComboBox<String>, items: List<String>, selected: String) {
        val distinct = items.distinct()
        combo.model = DefaultComboBoxModel(distinct.toTypedArray())
        combo.selectedItem = if (selected in distinct) selected else distinct.firstOrNull()
    }

    private fun selected(combo: ComboBox<String>, fallback: String): String =
        combo.selectedItem as? String ?: fallback

    private fun state() = project.xmakeSettings.state

    override fun dispose() {}
}
