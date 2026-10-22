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
 * @file        CliCommandLine.kt
 *
 */
package io.xmake.utils.cli

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import io.xmake.project.toolkit.ToolkitHost

/** A CLI executable installed on one host; its path is meaningful only there. */
internal data class CliInstallation(val executable: String, val host: ToolkitHost) {
    init {
        require(executable.isNotBlank()) { "CLI executable path must not be blank" }
    }
}

/** Marks the command DSL so nested scopes cannot reach an outer command. */
@DslMarker
internal annotation class CliDsl

/**
 * One command line of a CLI tool, built action first. [XMakeCommandLine] adds the xmake
 * actions; plain system utilities, such as the PATH lookup, use this class directly.
 */
@CliDsl
internal open class CliCommandLine internal constructor(
    val installation: CliInstallation,
) : GeneralCommandLine(installation.executable) {

    init {
        withCharset(Charsets.UTF_8)
    }

    /** The arguments added so far, in order. */
    val arguments: List<String>
        get() = parametersList.list

    /** Appends raw arguments, for actions the tool DSL does not name. */
    fun args(vararg arguments: String): CliCommandLine = args(arguments.toList())

    /** Appends [arguments] in order. */
    fun args(arguments: Iterable<String>): CliCommandLine {
        addParameters(arguments.toList())
        return this
    }

    /** Appends [name] when [enabled] is true. */
    fun flag(name: String, enabled: Boolean = true): CliCommandLine {
        if (enabled) addParameter(name)
        return this
    }

    /** Appends [name] and [value] when [value] is not null. */
    private fun option(name: String, value: String?): CliCommandLine {
        value?.let { addParameters(listOf(name, it)) }
        return this
    }

    /** Appends the option in [value] when its right side is not null. */
    fun option(value: Pair<String, String?>): CliCommandLine {
        val (name, argument) = value
        return option(name, argument)
    }

    /** Appends the arguments of a user-entered command line. */
    fun parsedArgs(commandLine: String): CliCommandLine = args(ParametersListUtil.parse(commandLine))
}
