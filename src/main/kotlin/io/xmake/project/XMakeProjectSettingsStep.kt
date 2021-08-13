package io.xmake.project
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.platform.DirectoryProjectGenerator

open class XMakeProjectSettingsStep(generator: DirectoryProjectGenerator<XMakeConfigData>)
    : ProjectSettingsStepBase<XMakeConfigData>(generator, AbstractNewProjectStep.AbstractCallback())
