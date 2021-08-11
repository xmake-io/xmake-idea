import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.ui.layout.panel
import javax.swing.JComponent
import com.intellij.openapi.util.Disposer
import org.tboox.xmake.project.XMakeModuleBuilder
import org.tboox.xmake.project.XMakeNewProjectPanel


class XMakeSdkSettingsStep(
    private val context: WizardContext,
    private val configurationUpdaterConsumer: ((ModuleBuilder.ModuleConfigurationUpdater) -> Unit)? = null
) : ModuleWizardStep() {

    private val newProjectPanel = XMakeNewProjectPanel()

    override fun getComponent(): JComponent = panel {
        newProjectPanel.attachTo(this)
    }

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
}