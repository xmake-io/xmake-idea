/*!A Xmake integration in IntelliJ IDEA/Clion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2015-present, Xmake Open Source Community.
 *
 * @file        XMakeCommandLine.kt
 *
 */
package io.xmake.utils.cli

import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.ToolkitHostType.LOCAL

/** An xmake command line: the shared CLI grammar plus the xmake actions. */
@CliDsl
internal class XMakeCommandLine internal constructor(installation: CliInstallation) :
    CliCommandLine(installation) {

    private var action: String? = null

    /** Starts the one action [argument] names; a command line never carries two actions. */
    private fun beginAction(action: String, argument: String = action): XMakeCommandLine = apply {
        check(this.action == null) {
            "XMake command line already starts with '${this.action}'; add one action per command"
        }
        this.action = action
        args(argument)
    }

    /** Appends [name] as a build target; `all` becomes `-a`, blank and `default` add nothing. */
    fun target(name: String?): XMakeCommandLine = apply {
        when (name) {
            null, "", DEFAULT_TARGET -> Unit
            ALL_TARGET -> args("-a")
            else -> args(name)
        }
    }

    fun version(): XMakeCommandLine = beginAction("version", "--version")

    /** Adds `show`; [listing] selects one `-l` listing and [json] asks for the JSON form. */
    fun show(listing: String? = null, json: Boolean = false): XMakeCommandLine = apply {
        listing?.let {
            require(INFO_QUERY_PATTERN.matches(it)) { "Invalid XMake info query: $it" }
        }
        beginAction("show")
        listing?.let { option("-l" to it) }
        flag("--json", json)
    }

    /** Adds `create` for [directory]; [language] and [kind] are the `-l` and `-t` choices. */
    fun create(
        directory: String,
        language: String? = null,
        kind: String? = null,
    ): XMakeCommandLine = apply {
        beginAction("create")
        option("-P" to directory)
        option("-l" to language)
        option("-t" to kind)
    }

    fun build(configure: BuildScope.() -> Unit): XMakeCommandLine = apply {
        val options = BuildScope().apply(configure)
        beginAction("build")
        flag("-r", options.rebuild)
        flag("-y")
        flag("-v", options.verbose)
        target(options.target)
    }

    fun config(configure: ConfigScope.() -> Unit): XMakeCommandLine = apply {
        val options = ConfigScope().apply(configure)
        beginAction("config")
        flag("-c", options.clean)
        flag("-y", options.yes)
        args(options.arguments)
        flag("-v", options.verbose)
        option("-o" to options.outputDirectory)
    }

    fun clean(configure: CleanScope.() -> Unit): XMakeCommandLine = apply {
        val options = CleanScope().apply(configure)
        beginAction("clean")
        flag("-a", options.all)
        flag("-v", options.verbose)
    }

    fun run(configure: RunScope.() -> Unit): XMakeCommandLine = apply {
        val options = RunScope().apply(configure)
        beginAction("run")
        option("-w" to options.workingDirectory)
        target(options.target)
        if (options.parsedArguments.isNotEmpty()) {
            parsedArgs(options.parsedArguments)
        }
    }

    fun project(configure: ProjectScope.() -> Unit): XMakeCommandLine = apply {
        val options = ProjectScope().apply(configure)
        beginAction("project")
        option("-k" to options.kind)
        flag("-y", options.yes)
        options.lsp?.let { args("--lsp=$it") }
        options.outputDirectory?.let { args("--outputdir=$it") }
    }

    fun lua(configure: LuaScope.() -> Unit): XMakeCommandLine = apply {
        val options = LuaScope().apply(configure)
        beginAction("lua", "l")
        args(options.script)
        options.target
            ?.takeUnless { it.isBlank() || it == DEFAULT_TARGET }
            ?.let { args(it) }
    }

    internal companion object {
        private val INFO_QUERY_PATTERN = Regex("[a-z]+")
    }
}

/** The sentinel target id meaning no explicit target: builders omit the target argument. */
internal const val DEFAULT_TARGET = "default"
/** The xmake pseudo-target that names every target; [XMakeCommandLine.target] maps it to `-a`. */
internal const val ALL_TARGET = "all"

@CliDsl
internal class BuildScope internal constructor() {
    var target: String? = null
    var rebuild: Boolean = false
    var verbose: Boolean = false
}

@CliDsl
internal class ConfigScope internal constructor() {
    var clean: Boolean = false
    var yes: Boolean = false
    var arguments: List<String> = emptyList()
    var verbose: Boolean = false
    var outputDirectory: String? = null
}

@CliDsl
internal class CleanScope internal constructor() {
    var all: Boolean = false
    var verbose: Boolean = false
}

@CliDsl
internal class RunScope internal constructor() {
    var workingDirectory: String? = null
    var target: String? = null
    var parsedArguments: String = ""
}

@CliDsl
internal class ProjectScope internal constructor() {
    lateinit var kind: String
    var yes: Boolean = false
    var lsp: String? = null
    var outputDirectory: String? = null
}

@CliDsl
internal class LuaScope internal constructor() {
    lateinit var script: String
    var target: String? = null
}

/** Starts an xmake command line for [toolkit]. */
internal fun xmake(toolkit: Toolkit): XMakeCommandLine = xmake(CliInstallation(toolkit.path, toolkit.host))

/** Starts an xmake command line for [installation]. */
internal fun xmake(installation: CliInstallation): XMakeCommandLine = XMakeCommandLine(installation)

private val IS_WINDOWS_HOST = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

private val unixSearchPaths = listOf(
    // Todo: Add more paths
    $$"${HOME}/.local/bin",
    "/usr/local/bin",
    "/usr/bin",
    "/opt/homebrew/bin"
)

/**
 * Builds the PATH search that lists every xmake installation visible on [host].
 * A local Unix host searches the user's home before the system paths.
 */
internal fun locateXMakeCommand(host: ToolkitHost): CliCommandLine =
    if (host.type != LOCAL) {
        CliCommandLine(CliInstallation("sh", host)).args("-c", REMOTE_LOCATE_SCRIPT)
    } else if (IS_WINDOWS_HOST) {
        CliCommandLine(CliInstallation("where.exe", host)).args("xmake")
    } else {
        CliCommandLine(CliInstallation("which", host)).args(whichArguments(System.getProperty("user.home")))
    }

private fun whichArguments(home: String?): List<String> =
    listOf("xmake") + unixSearchPaths.map { path ->
        val resolved = home?.let { path.replace($$"${HOME}", it) } ?: path
        "$resolved/xmake"
    }

/** WSL and SSH must expand `home` remotely, so the search runs in a shell. */
private val REMOTE_LOCATE_SCRIPT = buildString {
    appendLine("command -v xmake")
    append("for path in ")
    unixSearchPaths.forEach { path ->
        append('"').append(path.replace($$"${HOME}", $$"$HOME")).append("/xmake\" ")
    }
    appendLine($$"""; do [ -x "$path" ] && echo "$path"; done""")
    appendLine("exit 0")
}
