package org.tboox.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.tboox.xmake.shared.XMakeConfiguration

class XMakeRunConfiguration(project: Project, name: String, factory: ConfigurationFactory
) : LocatableConfigurationBase(project, factory, name), RunConfigurationWithSuppressedDefaultDebugAction {

    // the current command line
    var currentCommandLine: GeneralCommandLine ?= null

    // save configuration
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeString("currentPlatform", XMakeConfiguration.currentPlatfrom)
        element.writeString("currentArchitecture", XMakeConfiguration.currentArchitecture)
        element.writeString("currentMode", XMakeConfiguration.currentMode)
        element.writeString("currentTarget", XMakeConfiguration.currentTarget)
        element.writeString("additionalConfiguration", XMakeConfiguration.additionalConfiguration)
        element.writeString("workingDirectory", XMakeConfiguration.workingDirectory)
        element.writeBool("verboseOutput", XMakeConfiguration.verboseOutput)
        XMakeConfiguration.environmentVariables.writeExternal(element)
    }

    // load configuration
    override fun readExternal(element: Element) {
        super.readExternal(element)
        XMakeConfiguration.cleanConfiguration(project)
        element.readString("currentPlatform")?.let { XMakeConfiguration.currentPlatfrom = it }
        element.readString("currentArchitecture")?.let { XMakeConfiguration.currentArchitecture = it }
        element.readString("currentMode")?.let { XMakeConfiguration.currentMode = it }
        element.readString("currentTarget")?.let { XMakeConfiguration.currentTarget = it }
        element.readString("additionalConfiguration")?.let { XMakeConfiguration.additionalConfiguration = it }
        element.readString("workingDirectory")?.let { XMakeConfiguration.workingDirectory = it }
        element.readBool("verboseOutput")?.let { XMakeConfiguration.verboseOutput = it }
        XMakeConfiguration.environmentVariables = EnvironmentVariablesData.readExternal(element)
    }

    override fun checkConfiguration() {
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XMakeRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return XMakeRunState(environment, currentCommandLine ?: XMakeConfiguration.runCommandLine)
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
