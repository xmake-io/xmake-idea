package io.xmake.project

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.ui.dsl.builder.panel
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

    override fun validate(): ValidationInfo? {
        with(newProjectPanel.data) {
            if (toolkit == null)
                return ValidationInfo("Toolkit is not set")

            // Todo: Check whether working directory is valid.
            if (toolkit.isOnRemote && remotePath.isNullOrBlank()) {
                return ValidationInfo("Working directory is not set!")
            }

            return null
        }
    }
}
