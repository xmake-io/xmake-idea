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