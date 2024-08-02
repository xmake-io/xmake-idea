package io.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.RemoteFilePath
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.project.xmakeConsoleView
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.SystemUtils
import org.jdom.Element

class XMakeRunConfiguration(
    project: Project, name: String, factory: ConfigurationFactory
) : LocatableConfigurationBase<RunProfileState>(project, factory, name),
    RunConfigurationWithSuppressedDefaultDebugAction {

    @OptionTag(tag = "activatedToolkit")
    var runToolkit: Toolkit? = null

    // the run target
    @OptionTag(tag = "target")
    var runTarget: String = "default"

    // the run arguments
    @OptionTag(tag = "arguments")
    var runArguments: String = ""

    // the run environmen
    @get:Transient
    var runEnvironment: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    @OptionTag(tag = "workingDirectory")
    var runWorkingDir: String = ""

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
            return project.xmakeConfiguration
                .makeCommandLine(parameters, runEnvironment)
                .withWorkDirectory(RemoteFilePath(runWorkingDir, true).ioFile)
                .withCharset(Charsets.UTF_8)
        }

    // save configuration
    override fun writeExternal(element: Element) {
        super.writeExternal(element)

        XmlSerializer.serializeInto(this, element)
        runEnvironment.writeExternal(element)
    }

    // load configuration
    override fun readExternal(element: Element) {
        super.readExternal(element)

        XmlSerializer.deserializeInto(this, element)
        runEnvironment = EnvironmentVariablesData.readExternal(element)
        runToolkit = runToolkit?.let { toolkit ->
            ToolkitManager.getInstance().findRegisteredToolkitById(toolkit.id)
        }
    }

    override fun checkConfiguration() {
        if (runToolkit == null) {
            throw RuntimeConfigurationError("Xmake toolkit is not set!")
        }

        // Todo: Check whether working directory is valid.
        if (runWorkingDir.isBlank()){
            throw RuntimeConfigurationError("Working directory is not set!")
        }
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        XMakeRunConfigurationEditor(project, this)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {

        // clear console first
        project.xmakeConsoleView.clear()

        // configure and run it
        val xmakeConfiguration = project.xmakeConfiguration
        if (xmakeConfiguration.changed) {
            SystemUtils.runvInConsole(project, xmakeConfiguration.configurationCommandLine)
                .addProcessListener(object : ProcessAdapter() {
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
