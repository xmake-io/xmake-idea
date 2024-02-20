package io.xmake.shared

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.xmake.utils.SystemUtils
import io.xmake.utils.ioRunvInPool

@State(name = "XMakeProjectSettings")
class XMakeConfiguration(// the project
    val project: Project
) : PersistentStateComponent<XMakeConfiguration.State>, ProjectComponent {

    // the platforms
    val platforms = arrayOf("macosx", "linux", "windows", "android", "iphoneos", "watchos", "mingw")

    // the architectures
    val architectures: Array<String>
        get() = getArchitecturesByPlatform(data.currentPlatform)

    // the modes
    val modes = arrayOf("release", "debug")

    // the build command line
    val buildCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf<String>("-y")
            if (data.verboseOutput) {
                parameters.add("-v")
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the rebuild command line
    val rebuildCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("-r", "-y")
            if (data.verboseOutput) {
                parameters.add("-v")
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the clean command line
    val cleanCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("c")
            if (data.verboseOutput) {
                parameters.add("-v")
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the clean configuration command line
    val cleanConfigurationCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("f", "-c", "-y")
            if (data.verboseOutput) {
                parameters.add("-v")
            }
            if (data.buildOutputDirectory != "") {
                parameters.add("-o")
                parameters.add(data.buildOutputDirectory)
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the configuration command line
    val configurationCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters =
                mutableListOf("f", "-y", "-p", data.currentPlatform, "-a", data.currentArchitecture, "-m", data.currentMode)
            if (data.currentPlatform == "android" && data.androidNDKDirectory != "") {
                parameters.add("--ndk=\"${data.androidNDKDirectory}\"")
            }
            if (data.verboseOutput) {
                parameters.add("-v")
            }
            if (data.buildOutputDirectory != "") {
                parameters.add("-o")
                parameters.add(data.buildOutputDirectory)
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the quick start command line
    val quickStartCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("f", "-y")
            if (data.verboseOutput) {
                parameters.add("-v")
            }

            // make command line
            return makeCommandLine(parameters)
        }

    val updateCmakeListsCommandLine: GeneralCommandLine
        get() = makeCommandLine(mutableListOf("project", "-k", "cmake", "-y"))

    val updateCompileCommansLine: GeneralCommandLine
        get() = makeCommandLine(mutableListOf("project", "-k", "compile_commands"))

    // the targets
    val targets: Array<String>
        get() {

            // make targets
            var targets = arrayOf("default", "all")
            val results = ioRunvInPool(
                listOf(
                    SystemUtils.xmakeProgram,
                    "l",
                    "-c",
                    "import(\"core.project.config\"); import(\"core.project.project\"); config.load(); for name, _ in pairs((project.targets())) do print(name) end"
                ), data.workingDirectory
            )
            results.forEach {
                if (it.trim() != "") {
                    targets += it
                }
            }
            return targets
        }

    // configuration is changed?
    var changed = true

    // the state data
    var _data: State = State()
    var data: State
        get() = _data
        set(value) {
            val newState = State(
                currentPlatform = value.currentPlatform,
                currentArchitecture = value.currentArchitecture,
                currentMode = value.currentMode,
                workingDirectory = value.workingDirectory,
                androidNDKDirectory = value.androidNDKDirectory,
                buildOutputDirectory = value.buildOutputDirectory,
                verboseOutput = value.verboseOutput,
                additionalConfiguration = value.additionalConfiguration
            )
            if (_data != newState) {
                _data = newState
                changed = true
            }
        }

    // make command line
    fun makeCommandLine(
        parameters: List<String>,
        environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT
    ): GeneralCommandLine {

        // make command
        return GeneralCommandLine(SystemUtils.xmakeProgram)
            .withParameters(parameters)
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(data.workingDirectory)
            .withEnvironment(environmentVariables.envs)
            .withRedirectErrorStream(true)
    }

    data class State(
        var currentPlatform: String = SystemUtils.platform(),
        var currentArchitecture: String = "",
        var currentMode: String = "release",
        var workingDirectory: String = "",
        var androidNDKDirectory: String = "",
        var buildOutputDirectory: String = "",
        var verboseOutput: Boolean = false,
        var additionalConfiguration: String = ""

    )

    // ensure state
    private fun ensureState() {
        if (data.workingDirectory == "") {
            data.workingDirectory = project.basePath.toString()
        }
        if (data.currentArchitecture == "" && architectures.isNotEmpty()) {
            data.currentArchitecture = architectures[0]
        }
    }

    // get and save state to file
    override fun getState(): State {
        return data
    }

    // load state from file
    override fun loadState(state: State) {
        data = state
        ensureState()
    }

    override fun initComponent() {
        ensureState()
    }

    override fun disposeComponent() {
    }

    override fun getComponentName(): String {
        return "XMakeConfiguration"
    }

    override fun projectOpened() {
        ensureState()
    }

    override fun projectClosed() {
    }

    companion object {

        // get architectures by platform
        fun getArchitecturesByPlatform(platform: String) = when (platform) {
            "macosx", "linux", "mingw" -> arrayOf("x86_64", "i386", "arm64")
            "windows" -> arrayOf("x86", "x64")
            "iphoneos" -> arrayOf("arm64", "armv7", "armv7s", "x86_64", "i386")
            "watchos" -> arrayOf("armv7s", "i386")
            "android" -> arrayOf("armv7-a", "armv5te", "armv6", "armv8-a", "arm64-v8a")
            else -> arrayOf()
        }

        // get log
        private val Log = Logger.getInstance(XMakeConfiguration::class.java.getName())
    }
}

val Project.xmakeConfiguration: XMakeConfiguration
    get() = this.getComponent(XMakeConfiguration::class.java)
        ?: error("Failed to get XMakeConfiguration for $this")

val Project.xmakeConfigurationOrNull: XMakeConfiguration?
    get() = this.getComponent(XMakeConfiguration::class.java) ?: null

