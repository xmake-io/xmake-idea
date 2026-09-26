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
package io.xmake.debug.clion.dap

import com.intellij.execution.ExecutionResult
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.dap.DapBreakpointsDescription
import com.intellij.platform.dap.DebugAdapterDescriptor
import com.intellij.platform.dap.connection.CommandLineDebugAdapterHandle
import com.intellij.platform.dap.connection.DebugAdapterHandle
import com.intellij.util.EnvironmentUtil
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrExceptionBreakpointType
import com.jetbrains.cidr.execution.debugger.breakpoints.CidrLineBreakpointType
import io.xmake.debug.DapDriverDetector
import io.xmake.debug.XMakeDebugDriver
import io.xmake.debug.XMakeDebugLaunch
import io.xmake.debug.clion.utils.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class XMakeDebugAdapterDescriptor(
    private val project: Project,
) : DebugAdapterDescriptor<XMakeDebugAdapterId>() {

    private var state: XMakeDapLaunchState? = null

    override val id: XMakeDebugAdapterId
        get() = XMakeDebugAdapterId

    override fun configureProfileState(environment: ExecutionEnvironment, state: RunProfileState) {
        this.state = requireNotNull(state as? XMakeDapLaunchState) {
            "XMake DAP requires XMakeDapLaunchState"
        }
    }

    override suspend fun launchDebugAdapter(
        environment: ExecutionEnvironment,
        executionResult: ExecutionResult?,
        sessionId: String,
    ): DebugAdapterHandle {
        val state = requireNotNull(this.state) { "XMake DAP requires a resolved launch" }
        val commandLine = createDriverCommandLine(state.launch, state.driver)
        diagnoseWindowsDriver(commandLine)
        return CommandLineDebugAdapterHandle(commandLine)
    }

    override val breakpointsDescription: DapBreakpointsDescription = DapBreakpointsDescription(
        CidrLineBreakpointType::class.java,
        CidrExceptionBreakpointType::class.java,
    )

    private fun createDriverCommandLine(launch: XMakeDebugLaunch, driver: XMakeDebugDriver.Dap): GeneralCommandLine =
        baseDriverCommandLine(launch, driver).apply {
            if (driver.info.type == DapDriverDetector.DapDriverType.GDB_DAP) {
                addParameters("-i", "dap")
            }
        }

    private fun baseDriverCommandLine(launch: XMakeDebugLaunch, driver: XMakeDebugDriver.Dap): GeneralCommandLine {
        val driverPath = driver.info.path
        val driverDirectory = File(driverPath).absoluteFile.parent
        val environment = EnvironmentUtil.getEnvironmentMap().toMutableMap().apply {
            putAll(launch.environment)
            prependPath(driverDirectory)
        }
        return GeneralCommandLine(driverPath)
            .withWorkDirectory(launch.workingDirectory.ifBlank { project.basePath })
            .withEnvironment(environment)
    }

    private fun MutableMap<String, String>.prependPath(directory: String?) {
        if (directory.isNullOrBlank()) return
        val key = keys.firstOrNull { it.equals("PATH", ignoreCase = true) }
            ?: if (SystemInfo.isWindows) "Path" else "PATH"
        val currentPath = get(key).orEmpty()
        put(key, if (currentPath.isBlank()) directory else "$directory${File.pathSeparator}$currentPath")
    }

    private fun diagnoseWindowsDriver(commandLine: GeneralCommandLine) {
        if (!SystemInfo.isWindows) return
        val driverPath = commandLine.exePath
        if (DIAGNOSTIC_CACHE.putIfAbsent(driverPath, true) != null) {
            return
        }

        val probe = GeneralCommandLine(driverPath)
            .withWorkDirectory(commandLine.workDirectory)
            .withEnvironment(commandLine.environment)
            .withParameters("--version")
        try {
            val process = probe.createProcess()
            if (!process.waitFor(DRIVER_DIAGNOSTIC_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                process.destroy()
                Logger.w(TAG, "Timed out while checking $driverPath")
                return
            }
            if (process.exitValue() == STATUS_DLL_NOT_FOUND.toInt()) {
                notifyMissingDriverDependency(driverPath)
            }
        } catch (error: Exception) {
            Logger.w(TAG, "Failed to check $driverPath: ${error.message}")
        }
    }

    private fun notifyMissingDriverDependency(driverPath: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("XMake.NotificationGroup")
            .createNotification(
                "DAP driver may be missing DLLs",
                "Failed to start ${File(driverPath).name} (0xC0000135). " +
                    "Run '$driverPath --version' in a terminal to identify the missing dependency.",
                NotificationType.ERROR,
            )
            .notify(project)
    }

    private companion object {
        const val TAG = "XMakeDebugAdapterDescriptor"
        const val DRIVER_DIAGNOSTIC_TIMEOUT_MS = 1_500L

        /** Windows exit code STATUS_DLL_NOT_FOUND (0xC0000135): the driver failed to load a required DLL. */
        const val STATUS_DLL_NOT_FOUND = 0xC0000135L

        /** Drivers are probed at most once per IDE session to avoid forking on every debug launch. */
        val DIAGNOSTIC_CACHE = ConcurrentHashMap<String, Boolean>()
    }
}
