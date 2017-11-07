package org.tboox.xmake.project

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.ide.util.projectWizard.SdkSettingsStep
import com.intellij.openapi.ui.ComboBox

class XMakeSdkSettingsStep(settingsStep: SettingsStep, moduleBuilder: ModuleBuilder) : SdkSettingsStep(settingsStep, moduleBuilder, { sdkTypeId -> XMakeSdkType.instance === sdkTypeId }) {

    // the module kinds
    private val kindsComboBox = ComboBox<String>((moduleBuilder as XMakeModuleBuilder).kindsModel)

    // the module languages
    private val languagesComboBox = ComboBox<String>((moduleBuilder as XMakeModuleBuilder).languagesModel)

    init {
        settingsStep.addSettingsField("Module Language", languagesComboBox)
        settingsStep.addSettingsField("Module Type", kindsComboBox)
    }

    override fun updateDataModel() {
        super.updateDataModel()
    }
}