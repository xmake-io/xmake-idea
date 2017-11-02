package org.tboox.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.tboox.xmake.shared.xmakeConfiguration

class XMakeRunConfiguration(project: Project, name: String, factory: ConfigurationFactory
) : LocatableConfigurationBase(project, factory, name), RunConfigurationWithSuppressedDefaultDebugAction {

    // the current command line
    var currentCommandLine: GeneralCommandLine ?= null

    // the run target
    var runTarget: String = "default"

    // the run environment
    var runEnvironment: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    // the run command line
    val runCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("run")
            if (runTarget == "all") {
                parameters.add("-a")
            } else if (runTarget != "" && runTarget != "default") {
                parameters.add(runTarget)
            }

            // make command line
            return project.xmakeConfiguration.makeCommandLine(parameters, runEnvironment)
        }

    // save configuration
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeString("runTarget", runTarget)
        runEnvironment.writeExternal(element)
    }

    // load configuration
    override fun readExternal(element: Element) {
        super.readExternal(element)
        runTarget = element.readString("runTarget") ?: "default"
        runEnvironment = EnvironmentVariablesData.readExternal(element)
    }

    override fun checkConfiguration() {
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XMakeRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return XMakeRunState(environment, currentCommandLine ?: runCommandLine)
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunConfiguration::class.java.getName())
    }
}


private fun Element.writeString(name: String, value: String) {
    val opt = org.jdom.Element("option")
    opt.setAttribute("name", name)
    opt.setAttribute("value", value)
    addContent(opt)
}

private fun Element.readString(name: String): String? = children.find { it.name == "option" && it.getAttributeValue("name") == name }?.getAttributeValue("value")


private fun Element.writeBool(name: String, value: Boolean) {
    writeString(name, value.toString())
}

private fun Element.readBool(name: String) = readString(name)?.toBoolean()
