package io.xmake.project
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.platform.DirectoryProjectGenerator

@Deprecated("Please refer to the relevant content in folder io/xmake/project/wizard.")
open class XMakeProjectSettingsStep(generator: DirectoryProjectGenerator<XMakeConfigData>)
    : ProjectSettingsStepBase<XMakeConfigData>(generator, AbstractNewProjectStep.AbstractCallback())
