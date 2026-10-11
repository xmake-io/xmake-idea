package io.xmake.run.command

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import java.nio.file.Path

internal fun interface RemotePathMapper {
    fun toLocal(remotePath: Path): Path?

    companion object {
        fun forToolkit(toolkit: Toolkit, workingDirectory: String, project: Project): RemotePathMapper = when (toolkit.host.type) {
            ToolkitHostType.LOCAL -> RemotePathMapper { it }

            ToolkitHostType.WSL -> WslMapper(toolkit.host.wslDistribution)

            ToolkitHostType.SSH -> SshMapper(workingDirectory, project)
        }
    }
}

private class WslMapper(private val distribution: WSLDistribution?) : RemotePathMapper {
    override fun toLocal(remotePath: Path): Path? =
        distribution?.getWindowsPath(remotePath.toString())?.let(Path::of)
}

private class SshMapper(private val remoteRoot: String, private val project: Project) : RemotePathMapper {
    override fun toLocal(remotePath: Path): Path? {
        val text = FileUtilRt.toSystemIndependentName(remotePath.toString())
        val rootPrefix = "${remoteRoot.trimEnd('/')}/"
        if (!text.startsWith(rootPrefix)) return null
        val localRoot = runCatching {
            Path.of(project.xmakeProjectDirectories.resolveLocalSyncDirectory())
        }.getOrNull() ?: return null
        return runCatching { localRoot.resolve(text.removePrefix(rootPrefix)).normalize() }.getOrNull()
    }
}
