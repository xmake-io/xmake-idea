package io.xmake.project.directory

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.project.Project
import com.intellij.util.IncorrectOperationException
import io.xmake.utils.path.WorkingDirectoryResolver
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

internal fun validateAbsoluteHostDirectory(hostDirectoryPath: String): String {
    if (!hostDirectoryPath.startsWith('/')) {
        throw RuntimeConfigurationError("XMake project directory must be an absolute host path")
    }
    return hostDirectoryPath
}

internal fun validateLocalDirectory(project: Project, directory: String): String {
    val resolvedDirectoryPath = try {
        WorkingDirectoryResolver.resolve(project, directory, validation = true)
    } catch (error: IncorrectOperationException) {
        throw RuntimeConfigurationError(error.localizedMessage ?: "XMake project directory contains invalid macros")
    }
    val directoryPath = try {
        Path.of(resolvedDirectoryPath)
    } catch (_: InvalidPathException) {
        throw RuntimeConfigurationError("XMake project directory is invalid: $resolvedDirectoryPath")
    }
    if (!Files.isDirectory(directoryPath)) {
        throw RuntimeConfigurationError("XMake project directory does not exist: $resolvedDirectoryPath")
    }
    if (!Files.isRegularFile(directoryPath.resolve("xmake.lua"))) {
        throw RuntimeConfigurationError(
            "XMake project directory does not contain a root xmake.lua file: $resolvedDirectoryPath"
        )
    }
    return resolvedDirectoryPath
}
