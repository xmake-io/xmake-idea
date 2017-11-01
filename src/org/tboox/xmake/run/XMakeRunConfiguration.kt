package org.tboox.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.tboox.xmake.project.XMakeProjectConfiguration

class XMakeRunConfiguration(project: Project, name: String, factory: ConfigurationFactory
) : LocatableConfigurationBase(project, factory, name), RunConfigurationWithSuppressedDefaultDebugAction {

    // the current command line
    var currentCommandLine: GeneralCommandLine ?= null
    
    // the project configuration
    val projectConfiguration = project.getComponent(XMakeProjectConfiguration::class.java)

    // save configuration
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeString("currentPlatform", projectConfiguration.currentPlatfrom)
        element.writeString("currentArchitecture", projectConfiguration.currentArchitecture)
        element.writeString("currentMode", projectConfiguration.currentMode)
        element.writeString("currentTarget", projectConfiguration.currentTarget)
        element.writeString("additionalConfiguration", projectConfiguration.additionalConfiguration)
        element.writeString("workingDirectory", projectConfiguration.workingDirectory)
        element.writeBool("verboseOutput", projectConfiguration.verboseOutput)
        projectConfiguration.environmentVariables.writeExternal(element)
    }

    // load configuration
    override fun readExternal(element: Element) {
        super.readExternal(element)
        element.readString("currentPlatform")?.let { projectConfiguration.currentPlatfrom = it }
        element.readString("currentArchitecture")?.let { projectConfiguration.currentArchitecture = it }
        element.readString("currentMode")?.let { projectConfiguration.currentMode = it }
        element.readString("currentTarget")?.let { projectConfiguration.currentTarget = it }
        element.readString("additionalConfiguration")?.let { projectConfiguration.additionalConfiguration = it }
        element.readString("workingDirectory")?.let { projectConfiguration.workingDirectory = it }
        element.readBool("verboseOutput")?.let { projectConfiguration.verboseOutput = it }
        projectConfiguration.environmentVariables = EnvironmentVariablesData.readExternal(element)
    }

    override fun checkConfiguration() {
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XMakeRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return XMakeRunState(environment, currentCommandLine ?: projectConfiguration.runCommandLine)
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
