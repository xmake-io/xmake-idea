// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Modifications:
 * - Changed class from final to open
 * - Date: 2024-08-07
 * - Author: windchargerj
 * - Reference: [com.jetbrains.python.newProject.NewProjectWizardDirectoryGeneratorAdapter]
 */

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
 * @file        NewProjectWizardDirectoryGeneratorAdapter.kt
 *
 */
package io.xmake.project.wizard

import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseData.Companion.baseData
import com.intellij.ide.wizard.NewProjectWizardStepPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.platform.ProjectGeneratorPeer
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

private val Log = logger<NewProjectWizardDirectoryGeneratorAdapter<*>>()

// Todo: Refactor to transform a project wizard to a [com.intellij.platform.DirectoryProjectGenerator] directly.

/**
 * A base adapter class to turn a [GeneratorNewProjectWizard] into a
 * [com.intellij.platform.DirectoryProjectGenerator] and register as an extension point.
 *
 * @see NewProjectWizardProjectSettingsStep
 */
open class NewProjectWizardDirectoryGeneratorAdapter<T : Any>(val wizard: GeneratorNewProjectWizard) :
    DirectoryProjectGeneratorBase<T>() {
    internal lateinit var panel: NewProjectWizardStepPanel

    /** Owns the peer's [WizardContext]; parented to the settings step inside [createPeer]. */
    internal lateinit var wizardDisposable: Disposable

    @Suppress("DialogTitleCapitalization")
    override fun getName(): String = wizard.name
    override fun getLogo(): Icon = wizard.icon

    override fun generateProject(project: Project, baseDir: VirtualFile, settings: T, module: Module) {
        try {
            panel.step.setupProject(project)
        } catch (error: ProcessCanceledException) {
            throw error
        } catch (error: Exception) {
            // The welcome-screen generator chain has no outer handler: report the failure
            // instead of killing the IDE with an uncaught exception.
            Messages.showErrorDialog(project, error.message, wizard.name)
            Log.warn("XMake project generation failed", error)
        }
    }

    override fun createPeer(): ProjectGeneratorPeer<T> {
        wizardDisposable = Disposer.newDisposable("XMake wizard context")
        val context = WizardContext(null, wizardDisposable)
        return object : GeneratorPeerImpl<T>() {
            override fun getComponent(myLocationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
                panel = NewProjectWizardStepPanel(wizard.createStep(context))
                return panel.component
            }
        }
    }
}

/**
 * A wizard-enabled project settings step that you should use for your [projectGenerator] in your
 * [AbstractNewProjectStep.Customization.createProjectSpecificSettingsStep] to provide the project wizard UI and actions.
 */
open class NewProjectWizardProjectSettingsStep<T : Any>(private val projectGenerator: NewProjectWizardDirectoryGeneratorAdapter<T>) :
    ProjectSettingsStepBase<T>(projectGenerator, null) {

    init {
        myCallback = AbstractNewProjectStep.AbstractCallback()
    }

    override fun createAndFillContentPanel(): JPanel {
        // peer.getComponent materializes the lazy peer, whose creation builds the wizard
        // context's disposable; parent it to this step only once that disposable exists,
        // because this step is constructed long before any panel is shown.
        val content = JPanel(VerticalFlowLayout()).apply {
            add(peer.getComponent(TextFieldWithBrowseButton()) {})
        }
        Disposer.register(this, projectGenerator.wizardDisposable)
        return content
    }

    override fun registerValidators() {}

    override fun getProjectLocation(): String {
        // The platform's close action listener reads the location before the apply listener
        // appended in getActionButton runs, so derive it from the live base data instead of
        // the WizardContext value that apply would have written.
        val base = projectGenerator.panel.step.baseData ?: return super.getProjectLocation()
        return Path.of(base.path).resolve(base.name).toString()
    }

    override fun getActionButton(): JButton =
        super.getActionButton().apply {
            addActionListener {
                projectGenerator.panel.apply()
            }
        }
}