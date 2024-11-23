package io.xmake.utils.extension

import ai.grazie.utils.tryRunWithException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.text.Formats
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ssh.*
import com.intellij.ssh.channels.SftpChannel
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ssh.config.unified.SshConfigManager
import com.intellij.ssh.interaction.PlatformSshPasswordProvider
import com.intellij.ssh.ui.sftpBrowser.RemoteBrowserDialog
import com.intellij.ssh.ui.sftpBrowser.SftpRemoteBrowserProvider
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.utils.execute.SyncDirection
import io.xmake.utils.execute.rmRecur
import kotlinx.coroutines.*
import java.awt.event.ActionListener
import java.io.File
import kotlin.io.path.Path

class SshToolkitHostExtensionImpl : ToolkitHostExtension {

    override val KEY: String = "SSH"

    private val sshConfigManager = SshConfigManager.getInstance(null)

    override fun getHostType(): String {
        return "SSH"
    }

    override fun getToolkitHosts(project: Project?): List<ToolkitHost> {
        return sshConfigManager.configs.map {
            ToolkitHost(ToolkitHostType.SSH, it)
        }
    }

    override fun filterRegistered(): (Toolkit) -> Boolean {
        return { it.isOnRemote }
    }

    override fun createToolkit(host: ToolkitHost, path: String, version: String): Toolkit {
        val sshConfig = (host.target as? SshConfig) ?: throw IllegalArgumentException()
        val name = sshConfig.presentableShortName
        return Toolkit(name, host, path, version)
    }

    override fun syncProject(
        scope: CoroutineScope,
        project: Project,
        host: ToolkitHost,
        direction: SyncDirection,
        remoteDirectory: String,
    ) {
        val sshConfig = (host.target as? SshConfig) ?: throw IllegalArgumentException()

        ProgressManager.getInstance().runProcessWithProgressAsynchronously(
            object : Task.Backgroundable(project, "Sync directory", true) {
                override fun run(indicator: ProgressIndicator) {
                    val commandString = SftpChannelConfig.SftpCommand.detectSftpCommandString
                    Log.info("Command: $commandString")

                    val builder = ConnectionBuilder(sshConfig.host)
                        .withSshPasswordProvider(PlatformSshPasswordProvider(sshConfig.copyToCredentials()))

                    val sourceRoots = ProjectRootManager.getInstance(project).contentRoots
                    Log.info("Source roots: $sourceRoots")
                    Log.info("guessProjectDir: " + project.guessProjectDir())

                    scope.launch {
                        val sftpChannel = builder.openFailSafeSftpChannel()
                        Log.info("sftpChannel.home" + sftpChannel.home)

                        when (direction) {
                            SyncDirection.LOCAL_TO_UPSTREAM -> {

                                Log.runCatching {
                                    tryRunWithException<SftpChannelNoSuchFileException, List<SftpChannel.FileInfo>> {
                                        sftpChannel.ls(
                                            remoteDirectory
                                        )
                                    }.also { Log.info("before: $it") }
                                    sftpChannel.rmRecur(remoteDirectory)
                                    Log.info("after: " + sftpChannel.ls("Project"))
                                }

                                sftpChannel.uploadFileOrDir(
                                    File(project.guessProjectDir()?.path ?: ""),
                                    remoteDir = remoteDirectory, relativePath = "/",
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
                                            .all {
                                                !file.startsWith(
                                                    Path(
                                                        project.guessProjectDir()?.path ?: "",
                                                        it
                                                    ).toFile()
                                                )
                                            }
                                    }, persistExecutableBit = true
                                )
                            }

                            SyncDirection.UPSTREAM_TO_LOCAL -> {
                                sftpChannel.downloadFileOrDir(remoteDirectory, project.guessProjectDir()?.path ?: "")
                            }
                        }
                        sftpChannel.close()

                        withContext(Dispatchers.EDT) {
                            runWriteAction {
                                VirtualFileManager.getInstance().syncRefresh()
                            }
                        }

                    }
                }

                override fun onCancel() {}

                override fun onFinished() {}
            },
            ProgressIndicatorBase()
        )
    }

    override suspend fun ToolkitHost.loadTargetX(project: Project?) = coroutineScope {
        target = SshConfigManager.getInstance(project).findConfigById(id!!)!!
    }

    override fun getTargetId(target: Any?): String {
        val sshConfig = target as? SshConfig ?: throw IllegalArgumentException()
        return sshConfig.id
    }

    override fun DirectoryBrowser.createBrowseListener(host: ToolkitHost): ActionListener {
        val sshConfig = host.target as? SshConfig ?: throw IllegalArgumentException()

        val sftpChannel = runBlocking(Dispatchers.Default) {
            ConnectionBuilder(sshConfig.host)
                .withSshPasswordProvider(PlatformSshPasswordProvider(sshConfig.copyToCredentials()))
                .openFailSafeSftpChannel()
        }
        val sftpRemoteBrowserProvider = SftpRemoteBrowserProvider(sftpChannel)
        val remoteBrowseFolderListener = ActionListener {
            text = RemoteBrowserDialog(
                sftpRemoteBrowserProvider,
                project,
                true,
                withCreateDirectoryButton = true
            ).apply { showAndGet() }.getResult()
        }
        return remoteBrowseFolderListener
    }

    override fun GeneralCommandLine.createProcess(host: ToolkitHost): Process {

        val sshConfig = host.target as? SshConfig ?: throw IllegalArgumentException()

        val builder = ConnectionBuilder(sshConfig.host)
            .withSshPasswordProvider(PlatformSshPasswordProvider(sshConfig.copyToCredentials()))

        val command = GeneralCommandLine("sh").withParameters("-c")
            .withParameters(this.commandLineString)
            .withWorkDirectory(workDirectory)
            .withCharset(charset)
            .withEnvironment(environment)
            .withInput(inputFile)
            .withRedirectErrorStream(isRedirectErrorStream)

        return builder
            .also { Log.info("commandOnRemote: ${command.commandLineString}") }
            .processBuilder(command)
            .start()
    }

    companion object {
        private val Log = logger<SshToolkitHostExtensionImpl>()
    }
}