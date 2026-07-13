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
 *
 * @author      ruki
 * @file        SshToolkitHostExtensionImpl.kt
 *
 */
package io.xmake.utils.extension

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ssh.*
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
        onComplete: () -> Unit,
    ) {
        val sshConfig = (host.target as? SshConfig) ?: throw IllegalArgumentException()

        object : Task.Backgroundable(project, "Sync directory", true) {
            override fun run(indicator: ProgressIndicator) {
                val projectDirectory = project.guessProjectDir()?.path
                    ?: project.basePath
                    ?: throw IllegalStateException("Cannot resolve project directory")
                val projectDirectoryFile = File(projectDirectory)
                val builder = ConnectionBuilder(sshConfig.host)
                    .withSshPasswordProvider(PlatformSshPasswordProvider(sshConfig.copyToCredentials()))

                runBlocking(scope.coroutineContext) {
                    val sftpChannel = builder.openFailSafeSftpChannel()
                    try {
                        when (direction) {
                            SyncDirection.LOCAL_TO_UPSTREAM -> {
                                try {
                                    sftpChannel.rmRecur(remoteDirectory)
                                } catch (e: SftpChannelNoSuchFileException) {
                                    Log.debug("Remote sync directory does not exist yet: $remoteDirectory", e)
                                }

                                sftpChannel.uploadFileOrDir(
                                    projectDirectoryFile,
                                    remoteDir = remoteDirectory,
                                    relativePath = "/",
                                    progressTracker = object : SftpProgressTracker {
                                        override val isCanceled: Boolean
                                            get() = indicator.isCanceled

                                        override fun onBytesTransferred(count: Long) {}

                                        override fun onFileCopied(file: File) {}
                                    },
                                    filesFilter = { file ->
                                        mutableListOf(".xmake", ".idea", "build", ".gitignore")
                                            .all {
                                                !file.startsWith(
                                                    Path(projectDirectory, it).toFile()
                                                )
                                            }
                                    },
                                    persistExecutableBit = true,
                                )
                            }

                            SyncDirection.UPSTREAM_TO_LOCAL -> {
                                sftpChannel.downloadFileOrDir(remoteDirectory, projectDirectory)
                            }
                        }
                    } finally {
                        sftpChannel.close()
                    }

                    withContext(Dispatchers.EDT) {
                        runWriteAction {
                            VirtualFileManager.getInstance().syncRefresh()
                        }
                    }
                }
            }

            override fun onSuccess() {
                if (!project.isDisposed) {
                    onComplete()
                }
            }

            override fun onThrowable(error: Throwable) {
                Log.warn("Failed to sync SSH files", error)
            }
        }.queue()
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
