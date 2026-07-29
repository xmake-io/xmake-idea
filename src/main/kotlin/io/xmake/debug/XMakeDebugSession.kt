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
 * @author      ruki
 * @file        XMakeDebugSession.kt
 *
 */
package io.xmake.debug

import com.intellij.execution.ExecutionException
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.util.execution.ParametersListUtil
import com.intellij.xdebugger.XDebuggerManager
import io.xmake.project.console.XMakeConsole
import io.xmake.project.toolkit.activatedToolkit
import io.xmake.run.XMakeRunConfiguration
import io.xmake.shared.xmakeConfiguration
import io.xmake.utils.Logger
import io.xmake.utils.SystemUtils
import io.xmake.utils.execute.runProcess
import kotlinx.coroutines.runBlocking
import java.io.File

/** Prepares an XMake target and delegates the debugger session to the available IDE integration. */
class XMakeDebugSession(
    private val environment: ExecutionEnvironment,
    private val console: XMakeConsole,
) {

    private val project: Project = environment.project
    private val configuration: XMakeRunConfiguration = environment.runProfile as XMakeRunConfiguration

    fun startDebugSession(): com.intellij.execution.ui.RunContentDescriptor? {
        val debugSupport = XMakeDebugSupport.find()
            ?: throw ExecutionException("XMake debug support is not available in this IDE")

        Logger.i(TAG, "Logging mode: ${Logger.getLoggingMode()}")
        Logger.d(TAG, "Starting debug session for target: ${configuration.runTarget}")

        warnAboutBuildMode()
        buildProject()
        val launch = createDebugLaunch()
        val starter = debugSupport.createProcessStarter(launch, environment)

        return XDebuggerManager.getInstance(project)
            .newSessionBuilder(starter)
            .environment(environment)
            .startSession()
            .runContentDescriptor
    }

    private fun buildProject() {
        Logger.d(TAG, "Building project before debug...")
        val xmakeConfiguration = project.xmakeConfiguration
        console.clear()

        if (xmakeConfiguration.changed) {
            val configureProcess = SystemUtils.runvInConsole(
                project,
                console,
                xmakeConfiguration.configurationCommandLine,
            )
            configureProcess?.waitFor()
            xmakeConfiguration.changed = false
        }

        val buildCommandLine = xmakeConfiguration.makeCommandLine(
            listOf("build", configuration.runTarget).filter { it != "default" && it.isNotBlank() },
            EnvironmentVariablesData.DEFAULT,
        )
        val buildProcess = SystemUtils.runvInConsole(
            project,
            console,
            buildCommandLine,
            showConsole = false,
            showProblem = true,
            showExitCode = false,
        )
        buildProcess?.waitFor()
        Logger.d(TAG, "Build completed")
    }

    private fun warnAboutBuildMode() {
        if (configuration.runMode in DEBUG_BUILD_MODES) {
            Logger.v(TAG, "Build mode '${configuration.runMode}' is suitable for debugging")
            return
        }

        Logger.w(TAG, "Build mode '${configuration.runMode}' may not contain debug symbols")
        NotificationGroupManager.getInstance()
            .getNotificationGroup("XMake.NotificationGroup")
            .createNotification(
                "Build mode warning",
                "The current build mode '<b>${configuration.runMode}</b>' may not contain debug symbols.<br/>" +
                    "For better debugging experience, consider using 'debug' or 'releasedbg' mode.<br/>" +
                    "You can continue debugging, but some debugging features may be limited.",
                NotificationType.WARNING,
            )
            .notify(project)
    }

    private fun createDebugLaunch(): XMakeDebugLaunch {
        val targetName = configuration.runTarget
        val targetPath = getTargetExecutable(targetName)
        if (targetPath.isNullOrBlank()) {
            showBuildRequiredNotification(targetName, "Could not determine target executable path")
            throw ExecutionException("Target executable not found for $targetName")
        }

        val target = File(targetPath)
        if (!target.isFile) {
            showBuildRequiredNotification(targetName, "Target executable not found: $targetPath")
            throw ExecutionException("Target executable not found: $targetPath")
        }

        val driverPath = configuration.getEffectiveDapDriverPath()
        if (driverPath.isBlank()) {
            throw ExecutionException(
                "No DAP driver found. Please install lldb-dap or GDB with DAP support, " +
                    "or specify a custom path in the debug configuration.",
            )
        }
        val driver = DapDriverDetector.validateDriverPath(driverPath)
            ?: throw ExecutionException("Invalid DAP driver path: $driverPath")
        if (driver.type == DapDriverDetector.DapDriverType.GDB_DAP && !driver.dapCapable) {
            notifyUnsupportedGdb(driver)
            throw ExecutionException("GDB does not support DAP: $driverPath")
        }

        val workingDirectory = configuration.resolvedWorkingDirectory.takeIf(String::isNotBlank)
            ?: project.basePath
            ?: target.absoluteFile.parent
        return XMakeDebugLaunch(
            executablePath = target.absolutePath,
            driver = driver,
            launchConfiguration = configuration.launchConfiguration,
            arguments = configuration.runArguments
                .takeIf(String::isNotBlank)
                ?.let(ParametersListUtil::parse)
                ?: emptyList(),
            environment = configuration.runEnvironment.envs.toMap(),
            workingDirectory = workingDirectory,
        )
    }

    private fun notifyUnsupportedGdb(driver: DapDriverDetector.DapDriverInfo) {
        val detail = driver.diagnostics?.let { "<br/><br/>$it" }.orEmpty()
        NotificationGroupManager.getInstance()
            .getNotificationGroup("XMake.NotificationGroup")
            .createNotification(
                "GDB does not support DAP",
                "The selected GDB does not support Debug Adapter Protocol (DAP).<br/>" +
                    "Please install GDB 14.1+ (or use lldb-dap) and retry.<br/><br/>" +
                    "driver=${driver.path}$detail",
                NotificationType.ERROR,
            )
            .notify(project)
    }

    private fun showBuildRequiredNotification(targetName: String, reason: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("XMake.NotificationGroup")
            .createNotification(
                "Build required",
                "Target '<b>$targetName</b>' needs to be built before debugging.<br/><br/>" +
                    "Reason: $reason<br/><br/>" +
                    "Please build the project first using the XMake Build action.",
                NotificationType.ERROR,
            )
            .notify(project)
    }

    private fun getTargetExecutable(targetName: String): String? {
        Logger.v(TAG, "Getting executable path for target: $targetName")
        val xmakeConfiguration = project.xmakeConfiguration
        if (project.activatedToolkit == null) return null
        val targetPathScript = SystemUtils.getScriptPath("targetpath.lua") ?: return null
        val parameters = buildList {
            add("l")
            add(targetPathScript)
            if (targetName != "default" && targetName.isNotEmpty()) add(targetName)
        }
        val commandLine = xmakeConfiguration.makeCommandLine(
            parameters,
            EnvironmentVariablesData.DEFAULT,
        ).apply {
            withEnvironment("XMAKE_SKIP_HISTORY", "1")
            withEnvironment("XMAKE_ROOT", "y")
            withEnvironment("XMAKE_COLOR_TERM", "nocolor")
        }

        return runBlocking {
            val (result, _) = runProcess(commandLine.createProcess())
            val output = result.getOrNull()?.trim() ?: return@runBlocking null
            val path = "__begin__([\\s\\S]*?)__end__".toRegex()
                .find(output)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?: return@runBlocking null
            if (File(path).isAbsolute) path else File(project.basePath, path).absolutePath
        }
    }

    private companion object {
        const val TAG = "XMakeDebugSession"
        val DEBUG_BUILD_MODES = setOf("debug", "releasedbg")
    }
}
