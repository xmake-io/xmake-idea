package io.xmake.run

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.xmake.icons.XMakeIcons

class XMakeRunConfigurationType : ConfigurationTypeBase(
    "XMakeRunConfiguration",
    "XMake",
    "XMake run command configuration",
    XMakeIcons.XMAKE
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                XMakeRunConfiguration(project, "XMake", this)

            override fun configureBeforeRunTaskDefaults(
                providerID: Key<out BeforeRunTask<BeforeRunTask<*>>>,
                task: BeforeRunTask<out BeforeRunTask<*>>
            ) {

//                if (providerID == CompileStepBeforeRun.ID) {
//                    // We don't use jps, so we don't need to execute `Make` task
//                    // before run configuration is executed
//                    task.isEnabled = false
//                }
            }

            // This value gets written to the config file. By default it defers to getName, however,
            // the value needs to be kept the same even if the display name changes in the future
            // in order to maintain compatibility with older configs.
            override fun getId() = "Start and Debug"
        })
    }

    val factory: ConfigurationFactory get() = configurationFactories.single()

    companion object {
        fun getInstance(): XMakeRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(XMakeRunConfigurationType::class.java)

        // get log
        private val Log = Logger.getInstance(XMakeRunConfigurationType::class.java.getName())
    }
}
