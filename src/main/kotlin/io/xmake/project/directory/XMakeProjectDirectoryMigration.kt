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
    // Profile-based versions persisted a Windows path for WSL toolkits and mapped it into the
    // distribution at run time; only a '/'-prefixed value was an explicit distribution path.
    if (!legacy.directory.startsWith('/')) {
        return importLocalDirectory(legacy.directory)
    }
    // The run-configuration migration chain builds hosts without a resolved backend (see
    // migratedLegacyToolkit), so this Windows-path folding is only reachable from the
    // profile-based chain; '/'-prefixed directories otherwise stay as host entries, which
    // resolves to the same directory at run time.
    val windowsDirectoryPath = legacy.toolkit
        ?.host
        ?.wslDistribution
        ?.getWindowsPath(legacy.directory)
    if (windowsDirectoryPath != null) {
        return importLocalDirectory(windowsDirectoryPath)
    }
    return importHostDirectory(legacy, legacy.directory)
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
