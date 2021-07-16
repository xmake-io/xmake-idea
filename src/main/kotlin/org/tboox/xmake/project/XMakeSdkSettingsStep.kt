import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ui.configuration.SdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkComboBoxModel
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.ui.layout.panel
import org.jetbrains.annotations.Nullable
import org.tboox.xmake.project.XMakeSdkType
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import com.intellij.openapi.ui.ComboBox
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
            //TODO:数据联动
        }
    }
}