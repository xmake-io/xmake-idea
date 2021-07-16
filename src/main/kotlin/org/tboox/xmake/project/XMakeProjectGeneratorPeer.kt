package org.tboox.xmake.project

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.ui.layout.panel
import javax.swing.JComponent

class XMakeProjectGeneratorPeer : GeneratorPeerImpl<XMakeConfigData>() {

    private val newProjectPanel = XMakeNewProjectPanel()
    private var checkValid: Runnable? = null

    override fun getSettings(): XMakeConfigData = newProjectPanel.data

    override fun getComponent(myLocationField: TextFieldWithBrowseButton, checkValid: Runnable): JComponent {
        this.checkValid = checkValid
        return super.getComponent(myLocationField, checkValid)
    }

    override fun getComponent(): JComponent = panel {
        newProjectPanel.attachTo(this)
    }

}
