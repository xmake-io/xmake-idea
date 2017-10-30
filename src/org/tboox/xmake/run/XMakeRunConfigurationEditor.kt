package org.tboox.xmake.run

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.layout.panel
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel

class XMakeRunConfigurationEditor(private val project: Project) : SettingsEditor<XMakeRunConfiguration>() {

    override fun resetEditorFrom(configuration: XMakeRunConfiguration) {
        Log.info("resetEditorFrom")
    }

    @Throws(ConfigurationException::class)
    override fun applyEditorTo(configuration: XMakeRunConfiguration) {
        Log.info("applyEditorTo")
    }

    override fun createEditor(): JComponent = panel {
        Log.info("createEditor")
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunConfigurationEditor::class.java.getName())
    }
}
