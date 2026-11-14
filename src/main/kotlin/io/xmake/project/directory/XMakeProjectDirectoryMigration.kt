package io.xmake.project.directory

import com.intellij.openapi.diagnostic.Logger
import io.xmake.project.directory.XMakeProjectDirectoryState.HostDirectory
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType

/** A project directory that was persisted by an older build-profile-based version. */
data class LegacyProjectDirectory(val toolkit: Toolkit?, val directory: String) {
    val hostType: ToolkitHostType
        get() = toolkit?.host?.type ?: ToolkitHostType.LOCAL
}

internal fun XMakeProjectDirectoryState.migrateLegacyDirectories(
    legacyDirectories: List<LegacyProjectDirectory>,
): XMakeProjectDirectoryState =
    legacyDirectories.fold(this) { state, legacy -> state.importLegacyDirectory(legacy) }

private fun XMakeProjectDirectoryState.importLegacyDirectory(legacy: LegacyProjectDirectory): XMakeProjectDirectoryState =
    when (legacy.hostType) {
        ToolkitHostType.LOCAL -> importLocalDirectory(legacy.directory)
        ToolkitHostType.WSL -> importWslDirectory(legacy)
        ToolkitHostType.SSH -> importHostDirectory(legacy, legacy.directory)
    }

private fun XMakeProjectDirectoryState.importWslDirectory(legacy: LegacyProjectDirectory): XMakeProjectDirectoryState {
    /* Profile-based versions persisted a Windows path for WSL toolkits and mapped it into the
     * distribution at run time; only a '/'-prefixed value was an explicit distribution path. */
    if (!legacy.directory.startsWith('/')) {
        return importLocalDirectory(legacy.directory)
    }
    /* WSL now runs in the local project directory through the distribution mount, so an explicit
     * distribution path only survives as the local spelling of that same directory. The
     * run-configuration migration chain builds hosts without a resolved backend (see
     * migratedLegacyToolkit), so an offline distribution cannot be translated; dropping the
     * entry lets resolution fall back to the local project directory. */
    val windowsDirectoryPath = legacy.toolkit
        ?.host
        ?.wslDistribution
        ?.getWindowsPath(legacy.directory)
    if (windowsDirectoryPath == null) {
        Log.warn("Skipped legacy WSL directory that cannot be mapped to Windows: ${legacy.directory}")
        return this
    }
    return importLocalDirectory(windowsDirectoryPath)
}

private fun XMakeProjectDirectoryState.importLocalDirectory(directory: String): XMakeProjectDirectoryState =
    if (localDirectory.isBlank()) copy(localDirectory = directory) else this

private fun XMakeProjectDirectoryState.importHostDirectory(legacy: LegacyProjectDirectory, directory: String): XMakeProjectDirectoryState {
    val hostId = legacy.toolkit?.host?.id?.canonical
    if (hostId == null) {
        Log.warn("Skipped legacy directory without a resolvable host: $directory")
        return this
    }
    if (!directory.startsWith('/')) {
        Log.warn("Skipped legacy $hostId directory that is not an absolute host path: $directory")
        return this
    }
    if (hostDirectories.any { it.hostId == hostId }) return this
    val hostDirectory = HostDirectory(hostId, directory)
    return copy(hostDirectories = (hostDirectories + hostDirectory).toMutableList())
}

private val Log = Logger.getInstance("io.xmake.project.directory.XMakeProjectDirectoryMigration")
