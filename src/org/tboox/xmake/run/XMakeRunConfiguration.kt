package org.tboox.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.tboox.xmake.run.XMakeRunConfigurationEditor

class XMakeRunConfiguration(project: Project, name: String, factory: ConfigurationFactory
) : LocatableConfigurationBase(project, factory, name), RunConfigurationWithSuppressedDefaultDebugAction {

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        Log.info("writeExternal")
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        Log.info("readExternal")
    }

    @Throws(RuntimeConfigurationError::class)
    override fun checkConfiguration() {
        Log.info("checkConfiguration")
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XMakeRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        Log.info("getState")
        return null
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunConfiguration::class.java.getName())
    }
}
