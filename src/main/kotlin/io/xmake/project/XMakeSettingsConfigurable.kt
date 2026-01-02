package io.xmake.project

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class XMakeSettingsConfigurable(private val project: Project) : Configurable {
    private val settings = XMakeSettings.getInstance(project)
    private var myPanel: com.intellij.openapi.ui.DialogPanel? = null

    override fun createComponent(): JComponent {
        val panel = panel {
            group("Intellisense") {
                row("Compile commands path:") {
                    textField()
                        .bindText(settings.state::compileCommandsPath)
                        .comment("Path to generate compile_commands.json (relative to project root). Default: ./compile_commands.json")
                }
                row {
                    checkBox("Auto-update compile_commands.json after build")
                        .bindSelected(settings.state::autoUpdateCompileCommands)
                }
            }
        }
        myPanel = panel
        return panel
    }

    override fun isModified(): Boolean {
        return myPanel?.isModified() ?: false
    }

    override fun apply() {
        myPanel?.apply()
    }

    override fun reset() {
        myPanel?.reset()
    }

    override fun getDisplayName() = "Xmake"
}
