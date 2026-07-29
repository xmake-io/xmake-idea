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
package io.xmake.debug

import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import io.xmake.project.console.XMakeConsole
import io.xmake.run.command.XMakeConsoleOptions
import io.xmake.run.command.XMakeExecutionService
import io.xmake.run.state.XMakeDebugState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal suspend fun prepareXMakeDebugLaunch(
    state: XMakeDebugState,
    project: Project,
    console: XMakeConsole,
    execution: XMakeExecutionService,
): XMakeDebugLaunch {
    warnAboutBuildMode(project, state.buildMode)
    execution.execute(console, state.configureCommand)
    execution.execute(
        console,
        state.buildCommand,
        XMakeConsoleOptions(showConsole = false, showProblems = true),
    )
    val output = execution.captureStandardOutput(state.targetPathCommand)
    val target = resolveTarget(state, output)
    val driver = resolveDriver(state)
    if (driver.type == DapDriverDetector.DapDriverType.GDB_DAP && !driver.dapCapable) {
        notifyUnsupportedGdb(project, driver)
        throw ExecutionException("GDB does not support DAP: ${driver.path}")
    }
    return XMakeDebugLaunch(
        executablePath = target.absolutePath,
        driver = driver,
        launchConfiguration = state.launchConfiguration,
        arguments = state.arguments,
        environment = state.environment,
        workingDirectory = state.buildCommand.workingDirectory,
    )
}

private fun resolveTarget(state: XMakeDebugState, output: String): File {
    val path = "__begin__([\\s\\S]*?)__end__".toRegex()
        .find(output.trim())
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: throw ExecutionException("Could not determine the executable path for ${state.targetName}")

    val targetPath = if (File(path).isAbsolute) {
        path
    } else {
        File(state.targetPathCommand.workingDirectory, path).absolutePath
    }
    val target = File(targetPath)
    if (!target.isFile) throw ExecutionException("Target executable not found: $targetPath")
    return target
}

private fun resolveDriver(state: XMakeDebugState): DapDriverDetector.DapDriverInfo {
    val driverPath = if (!state.detectDapDriver && state.configuredDapDriverPath.isNotBlank()) {
        state.configuredDapDriverPath
    } else {
        DapDriverDetector.findBestDriver()?.path.orEmpty()
    }
    if (driverPath.isBlank()) {
        throw ExecutionException(
            "No DAP driver found. Please install lldb-dap or GDB with DAP support, " +
            "or specify a custom path in the debug configuration.",
        )
    }
    return DapDriverDetector.validateDriverPath(driverPath)
        ?: throw ExecutionException("Invalid DAP driver path: $driverPath")
}

private suspend fun notifyUnsupportedGdb(
    project: Project,
    driver: DapDriverDetector.DapDriverInfo,
) = withContext(Dispatchers.EDT) {
    if (project.isDisposed) return@withContext

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

private suspend fun warnAboutBuildMode(project: Project, buildMode: String) {
    if (buildMode in setOf("debug", "releasedbg")) return

    withContext(Dispatchers.EDT) {
        if (project.isDisposed) return@withContext
        NotificationGroupManager.getInstance()
            .getNotificationGroup("XMake.NotificationGroup")
            .createNotification(
                "Build mode warning",
                "The current build mode '<b>$buildMode</b>' may not contain debug symbols.<br/>" +
                    "For better debugging experience, consider using 'debug' or 'releasedbg' mode.<br/>" +
                    "You can continue debugging, but some debugging features may be limited.",
                NotificationType.WARNING,
            )
            .notify(project)
    }
}
