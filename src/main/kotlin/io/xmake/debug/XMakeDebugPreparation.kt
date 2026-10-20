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
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.xmake.utils.notify
import io.xmake.build.XMakeBuildTask
import io.xmake.build.XMakeBuildTaskExecution
import io.xmake.run.command.XMakeExecutionService
import io.xmake.run.state.XMakeDebugState
import java.io.File

/**
 * Builds the debug target through [com.intellij.task.ProjectTaskManager] (same path as the Build
 * action, so it shows in CLion's Build tool window). Must run *before* [prepareXMakeDebugLaunch]
 * acquires the XMake execution mutex — see [io.xmake.run.command.XMakeExecutionService.submitAfter].
 */
internal suspend fun prepareXMakeDebugBuild(project: Project, state: XMakeDebugState) {
    warnAboutBuildMode(project, state.buildMode)
    XMakeBuildTaskExecution.run(
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
    // The native path takes its debugger from the active debug profile; DAP settings only apply in DAP mode.
    val driver = if (state.useDapDriver) resolveDapDriver(project, state) else XMakeDebugDriver.Native
    return XMakeDebugLaunch(
        executablePath = debugTarget.executableFile.absolutePath,
        driver = driver,
        arguments = state.arguments,
        environment = state.environment,
        workingDirectory = state.launchWorkingDirectory
            ?: debugTarget.effectiveRunDirectory.absolutePath,
    )
}

private data class DebugTarget(
    val executableFile: File,
    val effectiveRunDirectory: File,
)

private fun resolveDebugTarget(state: XMakeDebugState, output: String): DebugTarget {
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
        ?: throw ExecutionException("Could not determine the executable path for ${state.targetName}")

    val resolvedTargetPath = if (File(targetPath).isAbsolute) {
        targetPath
    } else {
        File(state.targetPathCommand.workingDirectory, targetPath).absolutePath
    }
    val executableFile = File(resolvedTargetPath)
    if (!executableFile.isFile) throw ExecutionException("Target executable not found: $targetPath")

    val effectiveRunDirectoryPath = targetOutputLines.getOrNull(1)
    val effectiveRunDirectory = if (effectiveRunDirectoryPath == null) {
        executableFile.absoluteFile.parentFile
    } else if (File(effectiveRunDirectoryPath).isAbsolute) {
        File(effectiveRunDirectoryPath)
    } else {
        File(state.targetPathCommand.workingDirectory, effectiveRunDirectoryPath).absoluteFile
    }
    if (!effectiveRunDirectory.isDirectory) {
        throw ExecutionException("Target run directory not found: ${effectiveRunDirectory.absolutePath}")
    }
    return DebugTarget(executableFile.absoluteFile, effectiveRunDirectory)
}

private suspend fun resolveDapDriver(project: Project, state: XMakeDebugState): XMakeDebugDriver.Dap {
    val driverPath = if (!state.dapDriverAutoDetect) {
        state.configuredDapDriverPath.trim()
    } else {
        DapDriverDetector.findBestDriver()?.path.orEmpty()
    }
    if (driverPath.isBlank()) {
        throw ExecutionException(
            "No DAP driver found. Please install lldb-dap or GDB with DAP support, " +
            "or specify a custom path in the debug configuration.",
        )
    }
    val info = DapDriverDetector.resolve(driverPath)
        ?: throw ExecutionException("Invalid DAP driver path: $driverPath")
    if (info.type == DapDriverType.GDB && info.support !is DapSupport.Supported) {
        notifyGdbDapUnavailable(project, info)
        throw ExecutionException(
            if (info.support is DapSupport.Unsupported) {
                "GDB does not support DAP: ${info.path}"
            } else {
                "GDB DAP support could not be verified: ${info.path}"
            }
        )
    }
    return XMakeDebugDriver.Dap(info, state.launchConfiguration)
}

private fun notifyGdbDapUnavailable(
    project: Project,
    driver: DapDriverInfo,
) {
    val (title, message) = when (val support = driver.support) {
        is DapSupport.Unsupported -> "GDB does not support DAP" to (
            "The selected GDB does not support Debug Adapter Protocol (DAP).<br/>" +
                "${support.reason}<br/><br/>" +
                "Please use a GDB build with DAP support or lldb-dap, then retry.<br/><br/>" +
                "driver=${driver.path}"
            )
        is DapSupport.Unverified -> "GDB DAP support could not be verified" to (
            "Starting the DAP interpreter of the selected GDB failed or timed out.<br/>" +
                "Run '${driver.path} -i dap -batch' in a terminal to check the driver directly.<br/><br/>" +
                "driver=${driver.path}<br/><br/>${support.diagnostics}"
            )
        DapSupport.Supported -> return
    }
    project.notify(message, title = title)
}

private fun warnAboutBuildMode(project: Project, buildMode: String) {
    if (buildMode in setOf("debug", "releasedbg")) return

    project.notify(
        "The current build mode '<b>$buildMode</b>' may not contain debug symbols.<br/>" +
            "For better debugging experience, consider using 'debug' or 'releasedbg' mode.<br/>" +
            "You can continue debugging, but some debugging features may be limited.",
        NotificationType.WARNING,
            title = "Build mode warning",
    )
}
