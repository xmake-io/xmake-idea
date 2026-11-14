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
package io.xmake.clion.dap

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.dap.DapBreakpointsDescription
import com.intellij.platform.dap.DebugAdapterDescriptor
import com.intellij.platform.dap.connection.CommandLineDebugAdapterHandle
import com.intellij.platform.dap.connection.DebugAdapterHandle
import com.intellij.util.EnvironmentUtil
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrExceptionBreakpointType
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrLineBreakpointType
import io.xmake.debug.DapDriverDiagnostics
import io.xmake.debug.DapDriverType
import io.xmake.debug.GDB_DAP_INTERPRETER_ARGS
import io.xmake.debug.XMakeDebugDriver
import io.xmake.debug.XMakeDebugLaunch
import io.xmake.utils.notify
import java.io.File

internal class XMakeDebugAdapterDescriptor(
    private val project: Project,
) : DebugAdapterDescriptor<XMakeDebugAdapterId>() {

    private var state: XMakeDapLaunchState? = null

    override val id: XMakeDebugAdapterId
        get() = XMakeDebugAdapterId

    override fun configureProfileState(environment: ExecutionEnvironment, state: RunProfileState) {
        this.state = state as? XMakeDapLaunchState
            ?: throw ExecutionException("XMake DAP requires XMakeDapLaunchState, got ${state::class.java.name}")
    }

    override suspend fun launchDebugAdapter(
        environment: ExecutionEnvironment,
        executionResult: ExecutionResult?,
        sessionId: String,
    ): DebugAdapterHandle {
        val state = this.state
            ?: throw ExecutionException("XMake DAP launch is not resolved; the debug state was not prepared.")
        val commandLine = createDriverCommandLine(state.launch, state.driver)
        if (DapDriverDiagnostics.diagnoseWindowsDllFailure(commandLine)) {
            notifyMissingDriverDependency(commandLine.exePath)
        }
        return CommandLineDebugAdapterHandle(commandLine)
    }

    @Suppress("DEPRECATION")
    override val breakpointsDescription: DapBreakpointsDescription = createBreakpointsDescription()

    private fun createDriverCommandLine(launch: XMakeDebugLaunch, driver: XMakeDebugDriver.Dap): GeneralCommandLine {
        val driverPath = driver.info.path
        val driverDirectory = File(driverPath).absoluteFile.parent
        val environment = EnvironmentUtil.getEnvironmentMap().toMutableMap().apply {
            putAll(launch.environment)
            prependPath(driverDirectory)
        }
        return GeneralCommandLine(driverPath)
            .withWorkDirectory(launch.workingDirectory)
            .withEnvironment(environment)
            .apply {
                if (driver.info.type == DapDriverType.GDB) {
                    withParameters(GDB_DAP_INTERPRETER_ARGS)
                }
            }
    }

    /**
     * IntelliJ 2026.3 added a nullable function-breakpoint type to this constructor while removing
     * the old two-argument overload. The constructor is resolved reflectively: a compiled call to
     * either overload fails plugin verification against the platform that removed it.
     */
    private fun createBreakpointsDescription(): DapBreakpointsDescription {
        val lineBreakpointType = CidrLineBreakpointType::class.java
        val exceptionBreakpointType = CidrExceptionBreakpointType::class.java
        val (constructor, initargs) = DapBreakpointsDescription::class.java.constructors
            .mapNotNull { candidate ->
                if (!candidate.parameterTypes.all { it == Class::class.java }) return@mapNotNull null
                val initargs: Array<Any?> = when (candidate.parameterCount) {
                    2 -> arrayOf(lineBreakpointType, exceptionBreakpointType)
                    3 -> arrayOf(lineBreakpointType, exceptionBreakpointType, null)
                    else -> return@mapNotNull null
                }
                candidate to initargs
            }
            .firstOrNull()
            ?: throw IllegalStateException("No compatible DapBreakpointsDescription constructor")
        return constructor.newInstance(*initargs) as DapBreakpointsDescription
    }

    private fun MutableMap<String, String>.prependPath(directory: String?) {
        if (directory.isNullOrBlank()) return
        val key = keys.firstOrNull { it.equals("PATH", ignoreCase = true) }
            ?: if (SystemInfo.isWindows) "Path" else "PATH"
        val currentPath = get(key).orEmpty()
        put(key, if (currentPath.isBlank()) directory else "$directory${File.pathSeparator}$currentPath")
    }

    private fun notifyMissingDriverDependency(driverPath: String) {
        project.notify(
            "DAP driver is missing a DLL",
            "Failed to start ${File(driverPath).name} (0xC0000135). " +
                "Run '${StringUtil.escapeXmlEntities(driverPath)} --version' in a terminal to identify the missing dependency.",
        )
    }

}
