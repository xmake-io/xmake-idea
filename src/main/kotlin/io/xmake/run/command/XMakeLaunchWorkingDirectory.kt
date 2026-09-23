package io.xmake.run.command

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.project.Project
import com.intellij.util.IncorrectOperationException
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.utils.path.WorkingDirectoryResolver
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal fun resolveLaunchWorkingDirectory(
    project: Project,
    toolkit: Toolkit,
    configuredLaunchWorkingDirectory: String,
): String? {
    if (configuredLaunchWorkingDirectory.isBlank()) return null

    val resolvedLaunchWorkingDirectory = try {
        WorkingDirectoryResolver.resolve(project, configuredLaunchWorkingDirectory, toolkit)
    } catch (error: IncorrectOperationException) {
        throw RuntimeConfigurationError(error.localizedMessage ?: "Launch working directory contains invalid macros")
    }

    if (toolkit.host.type != ToolkitHostType.LOCAL) {
        if (!resolvedLaunchWorkingDirectory.startsWith('/')) {
            throw RuntimeConfigurationError(
                "Launch working directory must be an absolute ${toolkit.host.type} host path",
            )
        }
        return resolvedLaunchWorkingDirectory
    }

    val launchWorkingDirectoryPath = try {
        Path.of(resolvedLaunchWorkingDirectory)
    } catch (_: InvalidPathException) {
        throw RuntimeConfigurationError("Launch working directory is invalid: $resolvedLaunchWorkingDirectory")
    }
    if (!Files.isDirectory(launchWorkingDirectoryPath)) {
        throw RuntimeConfigurationError("Launch working directory does not exist: $resolvedLaunchWorkingDirectory")
    }
    return resolvedLaunchWorkingDirectory
}
