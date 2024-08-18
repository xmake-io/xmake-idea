package io.xmake.project

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBEmptyBorder
import io.xmake.project.toolkit.ToolkitHostType.*


class XMakeSdkSettingsStep(
    private val context: WizardContext,
    private val configurationUpdaterConsumer: ((ModuleBuilder.ModuleConfigurationUpdater) -> Unit)? = null
) : ModuleWizardStep() {

    private val newProjectPanel = XMakeNewProjectPanel()

    override fun getComponent(): JComponent = panel {
        newProjectPanel.attachTo(this)
    }.withBorder(JBEmptyBorder(20, 20, 20, 20))

    override fun disposeUIResources() = Disposer.dispose(newProjectPanel)

    override fun updateDataModel() {
        val data = newProjectPanel.data

        val projectBuilder = context.projectBuilder
        if (projectBuilder is XMakeModuleBuilder) {
            projectBuilder.configurationData = data
            projectBuilder.addModuleConfigurationUpdater(ConfigurationUpdater)
        } else {
            configurationUpdaterConsumer?.invoke(ConfigurationUpdater)
        }
    }

    private object ConfigurationUpdater : ModuleBuilder.ModuleConfigurationUpdater() {

        override fun update(module: Module, rootModel: ModifiableRootModel) {
        }
    }

    override fun validate(): Boolean {

        with(newProjectPanel.data) {
            if (toolkit == null) {
                throw RuntimeConfigurationError("Xmake toolkit is not set!")
            }

            // Todo: Check whether working directory is valid.
            if ((toolkit.host.type == WSL || toolkit.host.type == SSH) && remotePath.isNullOrBlank()) {
                throw RuntimeConfigurationError("Working directory is not set!")
            }
        }

        return true
    }
}