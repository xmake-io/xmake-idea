package org.tboox.xmake.shared

import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import org.tboox.xmake.utils.SystemUtils

object XMakeConfiguration {

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
    var workingDirectory = ""

    // the environment variables
    var environmentVariables: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    // the verbose output
    var verboseOutput = false

    // the run command line
    val runCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf("run")
            if (verboseOutput) {
                parameters.add("-v")
            }
            if (currentTarget == "all") {
                parameters.add("-a")
            } else if (currentTarget != "" && currentTarget != "default") {
                parameters.add(currentTarget)
            }

            // make command
            return GeneralCommandLine(SystemUtils.xmakeProgram)
                    .withParameters(parameters)
                    .withCharset(Charsets.UTF_8)
                    .withWorkDirectory(workingDirectory)
                    .withEnvironment(environmentVariables.envs)
                    .withRedirectErrorStream(true)
        }

    // the build command line
    val buildCommandLine: GeneralCommandLine
        get() {

            // make parameters
            val parameters = mutableListOf<String>()
            if (currentTarget != "" && currentTarget != "default") {
                parameters.add("build")
            }
            if (verboseOutput) {
                parameters.add("-v")
            } else {
                parameters.add("-w")
            }
            if (currentTarget != "" && currentTarget != "default") {
                parameters.add(currentTarget)
            } else if (currentTarget == "all") {
                parameters.add("-a")
            }

            // make command
            return GeneralCommandLine(SystemUtils.xmakeProgram)
                    .withParameters(parameters)
                    .withCharset(Charsets.UTF_8)
                    .withWorkDirectory(workingDirectory)
                    .withEnvironment(environmentVariables.envs)
                    .withRedirectErrorStream(true)
        }

    // the targets
    val targets: Array<String>
        get() {

            // make targets
            var targets = arrayOf("default", "all")
            val results = SystemUtils.ioRunv(listOf(SystemUtils.xmakeProgram, "l", "-c", "import(\"core.project.config\"); import(\"core.project.project\"); config.load(); for name, _ in pairs((project.targets())) do print(name) end"), XMakeConfiguration.workingDirectory)
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
