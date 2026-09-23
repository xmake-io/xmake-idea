package io.xmake.project.directory

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
    val windowsDirectoryPath = legacy.toolkit
        ?.host
        ?.wslDistribution
        ?.takeIf { legacy.directory.startsWith('/') }
        ?.getWindowsPath(legacy.directory)
    if (windowsDirectoryPath != null) {
        return importLocalDirectory(windowsDirectoryPath)
    }
    return importHostDirectory(legacy, legacy.directory)
}

private fun XMakeProjectDirectoryState.importLocalDirectory(directory: String): XMakeProjectDirectoryState =
    if (localDirectory.isBlank()) copy(localDirectory = directory) else this

private fun XMakeProjectDirectoryState.importHostDirectory(legacy: LegacyProjectDirectory, directory: String): XMakeProjectDirectoryState {
    val hostId = legacy.toolkit?.host?.id?.canonical ?: return this
    if (hostDirectories.any { it.hostId == hostId }) return this
    val hostDirectory = HostDirectory(hostId, directory)
    return copy(hostDirectories = (hostDirectories + hostDirectory).toMutableList())
}
