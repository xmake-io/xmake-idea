package org.tboox.xmake.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.layout.LayoutBuilder
import java.awt.BorderLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel

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
    private val kindsComboBox = ComboBox(kindsModel)

    // the module languages
    private val languagesComboBox = ComboBox(languagesModel)

    val data: XMakeConfigData
        get() = XMakeConfigData(
            languagesModel.selectedItem.toString().toLowerCase(),
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


    fun attachTo(layout: LayoutBuilder) = with(layout) {
        row("XMake SDK:") {
            val project = ProjectManager.getInstance().defaultProject
            val sdkModel = ProjectSdksModel()
            sdkModel.addSdk(XMakeSdkType.instance, XMakeSdkType.instance.suggestHomePath()!!, null);
            val myJdkComboBox = SdkComboBox(SdkComboBoxModel.createSdkComboBoxModel(project, sdkModel))
            wrapComponent(myJdkComboBox)(growX, pushX)
        }
        row("Module Language:") {
            wrapComponent(languagesComboBox)(growX, pushX)
        }
        row("Module Type:") {
            wrapComponent(kindsComboBox)(growX, pushX)
        }

        update()
    }

    private fun wrapComponent(component: JComponent): JComponent =
        JPanel(BorderLayout()).apply {
            add(component, BorderLayout.NORTH)
        }

    fun update() {

    }

    override fun dispose() {
    }

}