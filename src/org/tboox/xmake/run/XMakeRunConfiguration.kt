package org.tboox.xmake.run

import com.intellij.execution.Executor
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
        set(value) { _currentArchitecture = value }

    // the modes
    val modes = arrayOf("release", "debug")
    var currentMode = "release"

    // the targets
    val targets: Array<String>
        get() {
            return arrayOf("default", "all")
        }
    var currentTarget = "default"

    // the additional configuration
    var additionalConfiguration = ""

    // save configuration
    override fun writeExternal(element: Element) {
        super.writeExternal(element)
    }

    // load configuration
    override fun readExternal(element: Element) {
        super.readExternal(element)
    }

    override fun checkConfiguration() {
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XMakeRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState? {
        Log.info("getState")
        return null
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
