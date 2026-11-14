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
 */
@file:Suppress("UnstableApiUsage") // CidrDebugProfileManager is the only cross-module door
package io.xmake.clion.native

import com.intellij.cidr.debugger.profiles.CidrDebugProfileManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.system.OS
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains.Toolchain
import com.jetbrains.cidr.execution.Installer
import com.jetbrains.cidr.execution.RunParameters
import com.jetbrains.cidr.execution.TrivialInstaller
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriverConfiguration
import com.jetbrains.cidr.execution.debugger.backend.lldb.LLDBDriverConfiguration
import io.xmake.debug.XMakeDebugLaunch

internal class XMakeRunParameters(
    private val launch: XMakeDebugLaunch,
    project: Project,
) : RunParameters() {

    private val debuggerDriverConfiguration = currentProfileDriverConfiguration(project)

    override fun getInstaller(): Installer = TrivialInstaller(
        GeneralCommandLine(launch.executablePath)
            .withParameters(launch.arguments)
            .withEnvironment(launch.environment)
            .withWorkDirectory(launch.workingDirectory),
    )

    override fun getDebuggerDriverConfiguration(): DebuggerDriverConfiguration = debuggerDriverConfiguration

    override fun getArchitectureId(): String? = null

    private fun currentProfileDriverConfiguration(project: Project): DebuggerDriverConfiguration = try {
        val manager = CidrDebugProfileManager.getInstance()
        val profile = manager.getCurrentDebugProfile(project)
            ?: manager.getDebugProfiles(project).firstOrNull()
            ?: return LLDBDriverConfiguration()
        profile.createDriverConfiguration(project, isElevated = false, isEmulateTerminal = false, cppEnvironment)
    } catch (error: LinkageError) {
        Log.warn("CLion debug profiles are unavailable, falling back to the bundled LLDB", error)
        LLDBDriverConfiguration()
    }

    private companion object {
        val Log = logger<XMakeRunParameters>()

        val cppEnvironment = CPPEnvironment(Toolchain(OS.fromString(System.getProperty("os.name"))))
    }
}
