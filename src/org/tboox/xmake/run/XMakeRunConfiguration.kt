package org.tboox.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jdom.Element
import org.tboox.xmake.utils.SystemUtils

class XMakeRunConfiguration(project: Project, name: String, factory: ConfigurationFactory
) : LocatableConfigurationBase(project, factory, name), RunConfigurationWithSuppressedDefaultDebugAction {

    // the platforms
    val platforms = arrayOf("macosx", "linux", "windows", "android", "iphoneos", "watchos", "mingw")
    var currentPlatfrom = SystemUtils.platform()

    // the architectures
    val architectures: Array<String>
        get() = getArchitecturesByPlatform(currentPlatfrom)
    private var _currentArchitecture: String = ""
    var currentArchitecture: String
        get() {
            if (_currentArchitecture == "" && architectures.isNotEmpty()) {
                _currentArchitecture = architectures[0]
            }
            return _currentArchitecture
        }
        set(value) {
            _currentArchitecture = value
        }

    // the modes
    val modes = arrayOf("release", "debug")
    var currentMode = "release"

    // the working directory
    var workingDirectory = project.basePath.toString()

    // the environment variables
    var environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    // the verbose output
    var verboseOutput = false

    // the targets
    val targets: Array<String>
        get() {

            // make targets
            var targets = arrayOf("default", "all")
            val results = SystemUtils.ioRunv(listOf("xmake", "l", "-c", "import(\"core.project.config\"); import(\"core.project.project\"); config.load(); for name, _ in pairs((project.targets())) do print(name) end"), workingDirectory)
            results.split("\n").forEach {
                if (it.trim() != "") {
                    targets += it
                }
            }
            return targets
        }
    var currentTarget = "default"

    // the additional configuration
    var additionalConfiguration = ""

    // save configuration
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.writeString("currentPlatfrom", currentPlatfrom)
        element.writeString("currentArchitecture", currentArchitecture)
        element.writeString("currentMode", currentMode)
        element.writeString("currentTarget", currentTarget)
        element.writeString("additionalConfiguration", additionalConfiguration)
        element.writeString("workingDirectory", workingDirectory)
        element.writeBool("verboseOutput", verboseOutput)
        environmentVariables.writeExternal(element)
    }

    // load configuration
    override fun readExternal(element: Element) {
        super.readExternal(element)
        element.readString("currentPlatfrom")?.let { currentPlatfrom = it }
        element.readString("currentArchitecture")?.let { currentArchitecture = it }
        element.readString("currentMode")?.let { currentMode = it }
        element.readString("currentTarget")?.let { currentTarget = it }
        element.readString("additionalConfiguration")?.let { additionalConfiguration = it }
        element.readString("workingDirectory")?.let { workingDirectory = it }
        element.readBool("verboseOutput")?.let { verboseOutput = it }
        environmentVariables = EnvironmentVariablesData.readExternal(element)
    }

    override fun checkConfiguration() {
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XMakeRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        return XMakeRunState(environment, currentTarget, workingDirectory, environmentVariables, verboseOutput)
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeRunConfiguration::class.java.getName())

        // get architectures by platform
        fun getArchitecturesByPlatform(platform: String) = when (platform) {
            "macosx", "linux", "mingw" -> arrayOf("x86_64", "i386")
            "windows" -> arrayOf("x86", "x64")
            "iphoneos" -> arrayOf("arm64", "armv7", "armv7s", "x86_64", "i386")
            "watchos" -> arrayOf("armv7s", "i386")
            "android" -> arrayOf("armv7-a", "armv5te", "armv6", "armv8-a", "arm64-v8a")
            else -> arrayOf()
        }
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
