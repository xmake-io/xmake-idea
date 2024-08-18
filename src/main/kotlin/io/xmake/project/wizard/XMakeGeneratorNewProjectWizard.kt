package io.xmake.project.wizard

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import io.xmake.icons.XMakeIcons
import javax.swing.Icon

class XMakeGeneratorNewProjectWizard : GeneratorNewProjectWizard {
    override val icon: Icon = XMakeIcons.XMAKE
    override val id: String = "xmake"
    override val name: String = "XMake"
    override fun createStep(context: WizardContext): NewProjectWizardStep {
        return RootNewProjectWizardStep(context)
            .nextStep(::NewProjectWizardBaseStep)
            .nextStep(::XMakeProjectWizardStep)
    }
}