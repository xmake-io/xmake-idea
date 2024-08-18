package io.xmake.utils.execute

import ai.grazie.utils.tryRunWithException
import com.intellij.execution.RunManager
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.execution.wsl.sync.WslHashFilters
import com.intellij.execution.wsl.sync.WslSync
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.Formats
import com.intellij.ssh.ConnectionBuilder
import com.intellij.ssh.SftpChannelConfig
import com.intellij.ssh.SftpChannelNoSuchFileException
import com.intellij.ssh.SftpProgressTracker
import com.intellij.ssh.channels.SftpChannel
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ssh.interaction.PlatformSshPasswordProvider
import com.intellij.util.io.systemIndependentPath
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.run.XMakeRunConfiguration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import kotlin.io.path.Path

private val Log = fileLogger()

enum class SyncMode {
    SYNC_ONLY,
    FORCE_SYNC,
}

enum class SyncType {
    PROJECT_ONLY,
    FOLDERS_ONLY,
}

enum class SyncStatus {
    SUCCESS,
    FAILED,
}

enum class SyncDirection { LOCAL_TO_UPSTREAM, UPSTREAM_TO_LOCAL}

fun SyncDirection.toBoolean(): Boolean = when (this) {
    SyncDirection.LOCAL_TO_UPSTREAM -> false
    SyncDirection.UPSTREAM_TO_LOCAL -> true
}

fun syncProjectByWslSync(
    scope: CoroutineScope,
    project: Project,
    wslDistribution: WSLDistribution,
    wslPath: String,
    direction: SyncDirection
) {
    scope.launch {
        WslSync.syncWslFolders(
            WslPath.parseWindowsUncPath(wslPath)?.linuxPath ?: wslPath,
            project.guessProjectDir()!!.toNioPath(),
            wslDistribution,
            direction.toBoolean(),
            WslHashFilters.WslHashFiltersBuilder().build()
        )
    }
}

fun syncProjectBySftp(
    scope: CoroutineScope,
    project: Project,
    config: SshConfig,
    remotePath: String,
    direction: SyncDirection
) {
    val commandString = SftpChannelConfig.SftpCommand.detectSftpCommandString
    Log.info("Command: $commandString")

    val builder = ConnectionBuilder(config.host)
        .withSshPasswordProvider(PlatformSshPasswordProvider(config.copyToCredentials()))

    val sourceRoots = ProjectRootManager.getInstance(project).contentRoots
    Log.info("Source roots: $sourceRoots")

    Log.info("basePath: "+project.basePath)
    Log.info("projectFile: "+project.projectFile)
    Log.info("projectFilePath: "+project.projectFile)
    Log.info("guessProjectDir: "+project.guessProjectDir())

    scope.launch {
        val sftpChannel = builder.openFailSafeSftpChannel()
        Log.info("sftpChannel.home"+sftpChannel.home)

        when (direction) {
            SyncDirection.LOCAL_TO_UPSTREAM -> {

                Log.runAndLogException {
                    tryRunWithException<SftpChannelNoSuchFileException, List<SftpChannel.FileInfo>> {
                        sftpChannel.ls(
                            remotePath
                        )
                    }
                        .also { Log.info("before: $it") }
                    sftpChannel.rmRecur(remotePath)
                    Log.info("after: "+sftpChannel.ls("Project"))
                }

                sftpChannel.uploadFileOrDir(
                    File(project.basePath ?: ""),
                    remoteDir = remotePath, relativePath = "/",
                    progressTracker = object : SftpProgressTracker {
                        override val isCanceled: Boolean
                            get() = false
                        //                TODO("Not yet implemented")

                        override fun onBytesTransferred(count: Long) {
                            println("onBytesTransferred(${Formats.formatFileSize(count)})")
                        }

                        override fun onFileCopied(file: File) {
                            println("onFileCopied($file)")
                        }
                    }, filesFilter = { file ->
                        mutableListOf(".xmake", ".idea", "build", ".gitignore")
                            .all { !file.startsWith(Path(project.basePath ?: "", it).toFile()) }
                    }, persistExecutableBit = true)
            }
            SyncDirection.UPSTREAM_TO_LOCAL -> {
                sftpChannel.downloadFileOrDir(remotePath, project.basePath ?: "")
            }
        }
        sftpChannel.close()
    }
}

fun syncFileByToolkit(scope: CoroutineScope, project: Project, toolkit: Toolkit, remotePath: String, direction: SyncDirection) {
    val fileRootPath =
        (RunManager.getInstance(project).selectedConfiguration?.configuration as XMakeRunConfiguration).runWorkingDir

    val filePath = Path(fileRootPath, remotePath).systemIndependentPath

    when (toolkit.host.type) {
        ToolkitHostType.LOCAL -> {}
        ToolkitHostType.WSL -> {
            syncProjectByWslSync(scope, project, toolkit.host.target as WSLDistribution, filePath, direction)
        }
        ToolkitHostType.SSH -> {
            syncProjectBySftp(scope, project, toolkit.host.target as SshConfig, filePath, direction)
        }
    }
}