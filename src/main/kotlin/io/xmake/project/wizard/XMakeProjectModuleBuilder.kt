package io.xmake.project.wizard

import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter

class XMakeProjectModuleBuilder :
    GeneratorNewProjectWizardBuilderAdapter(XMakeGeneratorNewProjectWizard())

