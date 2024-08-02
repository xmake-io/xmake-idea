package io.xmake.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ComboBoxPredicate
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.project.toolkit.ui.ToolkitComboBox
import io.xmake.project.toolkit.ui.ToolkitListItem
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

    private var toolkit: Toolkit? = null
    private val toolkitComboBox = ToolkitComboBox(::toolkit)

    private val browser = DirectoryBrowser(ProjectManager.getInstance().defaultProject)

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
        row("Xmake Toolkit:") {
            cell(toolkitComboBox)
                .applyToComponent {
                    whenItemSelected { item ->
                        browser.removeBrowserAllListener()
                        (item as? ToolkitListItem.ToolkitItem)?.toolkit?.let {
                            browser.addBrowserListenerByToolkit(it)
                        }
                    }
                    activatedToolkit?.let { browser.addBrowserListenerByToolkit(it) }
                }
                .align(AlignX.FILL)
        }
        row("Module Language:") {
            comboBox(languagesModel).align(AlignX.FILL)
        }
        row("Module Type:") {
            comboBox(kindsModel).align(AlignX.FILL)
        }
        row("Remote Project Dir:") {
            cell(browser).align(AlignX.FILL)
        }.visibleIf(ComboBoxPredicate<ToolkitListItem>(toolkitComboBox) {
            val toolkit = (it as? ToolkitListItem.ToolkitItem)?.toolkit
            if (toolkit == null) false
            else toolkit.host.type == WSL || toolkit.host.type == SSH
        })
    }

    override fun dispose() {
    }

}