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
 * @file        XMakeProjectDirectoryGenerator.kt
 *
 */
package io.xmake.project.wizard

import com.intellij.facet.ui.ValidationResult
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.CustomStepProjectGenerator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.impl.welcomeScreen.AbstractActionWithPanel
import com.intellij.platform.DirectoryProjectGenerator

class XMakeProjectDirectoryGenerator :
    NewProjectWizardDirectoryGeneratorAdapter<XMakeNewProjectWizardData>(XMakeGeneratorNewProjectWizard()),
    CustomStepProjectGenerator<XMakeNewProjectWizardData> {

    private fun validate(): ValidationResult {
        return with(panel.component.validateAll()) {
            if (all { it.okEnabled }) ValidationResult.OK
            else find { !it.okEnabled }?.let { ValidationResult(it.message) } ?: ValidationResult("")
        }
    }

    override fun createStep(
        projectGenerator: DirectoryProjectGenerator<XMakeNewProjectWizardData>?,
        callback: AbstractNewProjectStep.AbstractCallback<XMakeNewProjectWizardData>?,
    ): AbstractActionWithPanel = object : NewProjectWizardProjectSettingsStep<XMakeNewProjectWizardData>(this) {
        override fun registerValidators() {
            setErrorText(validate().errorMessage)
            panel.step.propertyGraph.afterPropagation {
                setErrorText(validate().errorMessage)
            }
            Disposer.register(this) { }
        }
    }
}