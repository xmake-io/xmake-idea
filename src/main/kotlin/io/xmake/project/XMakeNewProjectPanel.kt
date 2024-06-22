package io.xmake.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.dsl.builder.*
import javax.swing.DefaultComboBoxModel

class XMakeNewProjectPanel : Disposable {

    // the module kinds
    private val kindsModel = DefaultComboBoxModel<String>().apply {
        addElement("Console")
        addElement("Static Library")
        addElement("Shared Library")
    }

    // the module languages
    private val languagesModel = DefaultComboBoxModel<String>().apply {
        addElement("C")
        addElement("C++")
        addElement("Rust")
        addElement("Dlang")
        addElement("Go")
        addElement("Swift")
        addElement("Objc")
        addElement("Objc++")
    }

    // the module kinds
    private val moduleComboBox = ComboBox(kindsModel)

    // the module languages
    private val languagesComboBox = ComboBox(languagesModel)

    val data: XMakeConfigData
        get() = XMakeConfigData(
            languagesModel.selectedItem.toString().lowercase(),
            template
        )

    // get template
    private val template: String
        get() = when (kindsModel.selectedItem.toString()) {
            "Console" -> "console"
            "Static Library" -> "static"
            "Shared Library" -> "shared"
            else -> "console"
        }

    fun attachTo(layout: Panel) = with(layout) {
        row("XMake SDK:") {
            val project = ProjectManager.getInstance().defaultProject
            val sdkModel = ProjectSdksModel()
            val xmakeProgram = XMakeSdkType.instance.suggestHomePath()
            if (xmakeProgram != null) {
                sdkModel.addSdk(XMakeSdkType.instance, xmakeProgram, null)
            }
            val myJdkComboBox = SdkComboBox(SdkComboBoxModel.createSdkComboBoxModel(
                project,
                sdkModel,
                {sdk -> sdk is XMakeSdkType},
                {sdk -> sdk is XMakeSdkType},)
            )
            cell(myJdkComboBox).align(AlignX.FILL)
        }
        row("Module Language:") {
            cell(languagesComboBox).align(AlignX.FILL)
        }
        row("Module Type:") {
            cell(moduleComboBox).align(AlignX.FILL)
        }

        update()
    }

    fun update() {

    }

    override fun dispose() {
    }

}