package org.tboox.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.tboox.xmake.project.xmakeConsoleView
import org.tboox.xmake.shared.xmakeConfiguration
import org.tboox.xmake.utils.SystemUtils

class XMakeRunConfiguration(project: Project, name: String, factory: ConfigurationFactory
) : LocatableConfigurationBase(project, factory, name), RunConfigurationWithSuppressedDefaultDebugAction {

    // the run target
    var runTarget: String = "default"

    // the run arguments
    var runArguments: String = ""

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
            if (runArguments != "") {
                runArguments.split(" ").forEach {
                    parameters.add(it)
                }
            }

            // make command line
            return project.xmakeConfiguration.makeCommandLine(parameters, runEnvironment)
        }

    // save configuration
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeString("runTarget", runTarget)
        element.writeString("runArguments", runArguments)
        runEnvironment.writeExternal(element)
    }

    // load configuration
    override fun readExternal(element: Element) {
        super.readExternal(element)
        runTarget = element.readString("runTarget") ?: "default"
        runArguments = element.readString("runArguments") ?: ""
        runEnvironment = EnvironmentVariablesData.readExternal(element)
    }

    override fun checkConfiguration() {
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XMakeRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {

        // clear console first
        project.xmakeConsoleView.clear()

        // configure and run it
        val xmakeConfiguration = project.xmakeConfiguration
        if (xmakeConfiguration.changed) {
            SystemUtils.runvInConsole(project, xmakeConfiguration.configurationCommandLine).addProcessListener(object: ProcessAdapter() {
                override fun processTerminated(e: ProcessEvent) {
                    SystemUtils.runvInConsole(project, runCommandLine, false, true, true)
                }
            })
            xmakeConfiguration.changed = false
        } else {
            SystemUtils.runvInConsole(project, runCommandLine, true, true, true)
        }

        // does not use builtin run console panel
        return null
    }

    companion object {
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
