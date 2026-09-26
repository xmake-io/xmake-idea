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
import io.xmake.build.XMakeBuildTask
import io.xmake.build.runXMakeBuildTask
import io.xmake.run.command.XMakeExecutionService
import io.xmake.run.state.XMakeDebugState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Builds the debug target through [com.intellij.task.ProjectTaskManager] (same path as the Build
 * action, so it shows in CLion's Build tool window). Must run *before* [prepareXMakeDebugLaunch]
 * acquires the XMake execution mutex — see [io.xmake.run.command.XMakeExecutionService.submitAfter].
 */
internal suspend fun prepareXMakeDebugBuild(project: Project, state: XMakeDebugState) {
    warnAboutBuildMode(project, state.buildMode)
    runXMakeBuildTask(
        project,
        XMakeBuildTask(
            presentableName = "Build '${state.targetName}'",
            commands = listOf(state.configureCommand, state.buildCommand),
        ),
    )
}

internal suspend fun prepareXMakeDebugLaunch(
    state: XMakeDebugState,
    project: Project,
    execution: XMakeExecutionService,
): XMakeDebugLaunch {
    val output = execution.captureStandardOutput(state.targetPathCommand)
    val debugTarget = resolveDebugTarget(state, output)
    val driver = resolveDriver(state)
    if (driver.type == DapDriverDetector.DapDriverType.GDB_DAP && !driver.dapCapable) {
        notifyUnsupportedGdb(project, driver)
        throw ExecutionException("GDB does not support DAP: ${driver.path}")
    }
    return XMakeDebugLaunch(
        executablePath = debugTarget.executableFile.absolutePath,
        driver = driver,
        launchConfiguration = state.launchConfiguration,
        arguments = state.arguments,
        environment = state.environment,
        workingDirectory = state.launchWorkingDirectory
            ?: debugTarget.effectiveRunDirectory.absolutePath,
    )
}

/** A built xmake target as reported by `targetpath.lua`. */
internal data class XMakeTargetLocation(
    val executableFile: File,
    val effectiveRunDirectory: File,
)

private fun resolveDebugTarget(state: XMakeDebugState, output: String): XMakeTargetLocation =
    resolveXMakeTargetLocation(state.targetName, output, state.targetPathCommand.workingDirectory)

/**
 * Parses the `targetpath.lua` query output: `__begin__`, the target file, optionally its effective
 * run directory (`set_rundir()`, else the target file's directory), then `__end__`, one per line.
 * Relative paths resolve against [workingDirectory].
 */
internal fun resolveXMakeTargetLocation(targetName: String, output: String, workingDirectory: String): XMakeTargetLocation {
    val targetOutputLines = "__begin__([\\s\\S]*?)__end__".toRegex()
        .find(output.trim())
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.lineSequence()
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toList()
        .orEmpty()

    val targetPath = targetOutputLines.firstOrNull()
        ?: throw ExecutionException("Could not determine the executable path for $targetName")

    fun resolve(path: String) = File(path).let { if (it.isAbsolute) it else File(workingDirectory, path) }.absoluteFile

    val executableFile = resolve(targetPath)
    if (!executableFile.isFile) throw ExecutionException("Target executable not found: ${executableFile.path}")

    val effectiveRunDirectory = targetOutputLines.getOrNull(1)
        ?.let(::resolve)
        ?: executableFile.parentFile
    if (!effectiveRunDirectory.isDirectory) {
        throw ExecutionException("Target run directory not found: ${effectiveRunDirectory.path}")
    }
    return XMakeTargetLocation(executableFile, effectiveRunDirectory)
}

private fun resolveDriver(state: XMakeDebugState): DapDriverDetector.DapDriverInfo {
    val driverPath = if (!state.autoDetectDapDriver && state.configuredDapDriverPath.isNotBlank()) {
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
