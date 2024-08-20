package io.xmake.shared

import com.intellij.execution.RunManager
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.run.XMakeRunConfiguration
import io.xmake.utils.exception.XMakeRunConfigurationNotSetException

@Service(Service.Level.PROJECT)
class XMakeConfiguration(val project: Project) {

    // the platforms
    val platforms = arrayOf("macosx", "linux", "windows", "android", "iphoneos", "watchos", "mingw")

    // the architectures
    val architectures: Array<String>
        get() = getArchitecturesByPlatform(configuration.runPlatform)

    // the modes
    val modes = arrayOf("release", "debug")

    val configuration: XMakeRunConfiguration
        get() {
            return RunManager.getInstance(project).selectedConfiguration?.configuration as? XMakeRunConfiguration
                ?: throw XMakeRunConfigurationNotSetException()
        }

    // the build command line
    val buildCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("-y")
            if (configuration.enableVerbose) {
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
            if (configuration.enableVerbose) {
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
            if (configuration.enableVerbose) {
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
            if (configuration.enableVerbose) {
                parameters.add("-v")
            }
            if (configuration.buildDirectory != "") {
                parameters.add("-o")
                parameters.add(configuration.buildDirectory)
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the configuration command line
    val configurationCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters =
                mutableListOf(
                    "f",
                    "-y",
                    "-p",
                    configuration.runPlatform,
                    "-a",
                    configuration.runArchitecture,
                    "-m",
                    configuration.runMode
                )
            if (configuration.runPlatform == "android" && configuration.androidNDKDirectory != "") {
                parameters.add("--ndk=\"${configuration.androidNDKDirectory}\"")
            }
            if (configuration.enableVerbose) {
                parameters.add("-v")
            }
            if (configuration.buildDirectory != "") {
                parameters.add("-o")
                parameters.add(configuration.buildDirectory)
            }
            if (configuration.additionalConfiguration != "") {
                parameters.add(configuration.additionalConfiguration)
            }

            // make command line
            return makeCommandLine(parameters)
        }

    // the quick start command line
    val quickStartCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("f", "-y")
            if (configuration.enableVerbose) {
                parameters.add("-v")
            }

            // make command line
            return makeCommandLine(parameters)
        }

    val updateCmakeListsCommandLine: GeneralCommandLine
        get() = makeCommandLine(mutableListOf("project", "-k", "cmake", "-y"))

    val updateCompileCommansLine: GeneralCommandLine
        get() = makeCommandLine(mutableListOf("project", "-k", "compile_commands"))


    // configuration is changed?
    var changed = true

    // make command line
    fun makeCommandLine(
        parameters: List<String>,
        environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT
    ): GeneralCommandLine {

        // make command
        return GeneralCommandLine(project.activatedToolkit!!.path)
            .withParameters(parameters)
            .withCharset(Charsets.UTF_8)
            // Todo: Check if correct.
            .withWorkDirectory(
                configuration.runWorkingDir
            )
            .withEnvironment(environmentVariables.envs)
            .withRedirectErrorStream(true)
    }

    // ensure state
    private fun ensureState() {
        if (configuration.runArchitecture == "" && architectures.isNotEmpty()) {
            configuration.runArchitecture = architectures[0]
        }
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
    get() = this.getService(XMakeConfiguration::class.java)
        ?: error("Failed to get XMakeConfiguration for $this")

val Project.xmakeConfigurationOrNull: XMakeConfiguration?
    get() = this.getService(XMakeConfiguration::class.java) ?: null

