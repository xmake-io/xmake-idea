package io.xmake.project.directory

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.project.Project
import com.intellij.util.IncorrectOperationException
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.utils.path.WorkingDirectoryResolver

/** Computes the directory xmake should run in. The rules are deliberately asymmetric: an
 *  explicit host directory always wins, WSL may otherwise map the local directory into the
 *  distribution, and SSH cannot be verified locally so it requires explicit configuration. */
internal class XMakeProjectDirectoryResolver(
    private val project: Project,
    private val state: XMakeProjectDirectoryState,
) {

    /** Whether any directory source exists: a host entry, the IDE project root, or a valid local
     *  directory. The IDE-root check is separate so an invalid configured directory cannot hide
     *  a valid IDE root from recovery UI. Checks are ordered cheap to expensive. */
    fun hasDirectorySource(): Boolean =
        state.hostDirectories.any { it.directory.isNotBlank() } ||
                project.hasRootXMakeLua ||
                resolutionSucceeds { resolveLocalProjectDirectory() }

    /** Whether [toolkit] has a usable project directory right now. Runs the full validation. */
    fun canResolve(toolkit: Toolkit): Boolean = resolutionSucceeds { resolveProjectDirectory(toolkit) }

    /** Resolves the directory to run xmake in for [toolkit]. */
    fun resolveProjectDirectory(toolkit: Toolkit): String {
        if (toolkit.path.isBlank()) {
            throw RuntimeConfigurationError("XMake toolkit path is not set")
        }
        if (toolkit.requiresBackend && !toolkit.host.hasBackend) {
            throw RuntimeConfigurationError("XMake ${toolkit.host.type} toolkit host is not available")
        }

        return when (toolkit.host.type) {
            ToolkitHostType.LOCAL -> resolveLocalProjectDirectory()
            ToolkitHostType.WSL -> findExplicitHostDirectoryPath(toolkit.host)
                ?: WorkingDirectoryResolver.resolve(project, resolveLocalProjectDirectory(), toolkit)
            ToolkitHostType.SSH -> findExplicitHostDirectoryPath(toolkit.host)
                ?: throw RuntimeConfigurationError(
                    "XMake SSH project directory is not configured for ${toolkit.host.displayName}",
                )
        }
    }

    /** Returns the local directory paired with a host-specific project location for file sync.
     *  Unlike [resolveLocalProjectDirectory] it does not require xmake.lua to exist yet, so it
     *  must not be merged with it: a freshly created project syncs before its first build. */
    fun resolveLocalSyncDirectory(): String {
        val configuredDirectoryPath = state.localDirectory
            .ifBlank { project.basePath }
            ?: throw RuntimeConfigurationError("XMake project directory is not configured")
        return try {
            WorkingDirectoryResolver.resolve(project, configuredDirectoryPath)
        } catch (error: IncorrectOperationException) {
            throw RuntimeConfigurationError(error.localizedMessage ?: "XMake project directory contains invalid macros")
        }
    }

    private fun resolveLocalProjectDirectory(): String {
        val configuredDirectoryPath = state.localDirectory
            .ifBlank { project.basePath?.takeIf(::hasRootXMakeLua) }
            ?: throw RuntimeConfigurationError("XMake project directory is not configured")
        return validateLocalDirectory(project, configuredDirectoryPath)
    }

    private fun findExplicitHostDirectoryPath(host: ToolkitHost): String? =
        state.hostDirectories.firstOrNull { it.hostId == host.id.canonical }
            ?.directory
            ?.takeUnless(String::isBlank)
            ?.let(::validateAbsoluteHostDirectory)

    private inline fun resolutionSucceeds(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (_: RuntimeConfigurationError) {
            false
        }
}
