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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.Messages
import com.intellij.ssh.ConnectionBuilder
import com.intellij.ssh.SftpProgressTracker
import com.intellij.ssh.SftpChannelNoSuchFileException
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ssh.config.unified.SshConfigManager
import com.intellij.ssh.channels.SftpChannel
import com.intellij.ssh.channels.isDir
import com.intellij.ssh.interaction.PlatformSshPasswordProvider
import com.intellij.ssh.processBuilder
import com.intellij.ssh.ui.sftpBrowser.RemoteBrowserDialog
import com.intellij.ssh.ui.sftpBrowser.SftpRemoteBrowserProvider
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.utils.execute.defaultSyncExcludedEntryNames
import io.xmake.utils.execute.SyncDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.awt.event.ActionListener
import java.io.File

class SshToolkitHostExtensionImpl : ToolkitHostExtension {

    override val hostType: ToolkitHostType = ToolkitHostType.SSH

    override fun getHosts(project: Project?): List<ToolkitHost> =
        SshConfigManager.getInstance(project).configs.map { ToolkitHost.ssh(it) }

    override suspend fun syncProject(
        project: Project,
        host: ToolkitHost,
        direction: SyncDirection,
        hostDirectory: String,
    ) {
        val sshConfig = host.requireSshConfig()
        val projectDirectory = project.guessProjectDir()?.path
            ?: project.basePath
            ?: throw IllegalStateException("Cannot resolve project directory")
        val projectDirectoryFile = File(projectDirectory)
        val builder = connectionBuilder(sshConfig)
        val cancellationContext = currentCoroutineContext()
        val sftpChannel = runInterruptible(Dispatchers.IO) {
            builder.openFailSafeSftpChannel()
        }

        try {
            runInterruptible(Dispatchers.IO) {
                cancellationContext.ensureActive()
                when (direction) {
                    SyncDirection.LOCAL_TO_REMOTE -> {
                        sftpChannel.pruneMissingEntries(projectDirectoryFile, hostDirectory) {
                            cancellationContext.ensureActive()
                        }
                        sftpChannel.uploadFileOrDir(
                            projectDirectoryFile,
                            remoteDir = hostDirectory,
                            relativePath = "/",
                            progressTracker = object : SftpProgressTracker {
                                override val isCanceled: Boolean
                                    get() = !cancellationContext.isActive

                                override fun onBytesTransferred(count: Long) {}

                                override fun onFileCopied(file: File) {}
                            },
                            filesFilter = { file ->
                                file.name !in defaultSyncExcludedEntryNames
                            },
                            persistExecutableBit = true,
                        )
                    }

                    SyncDirection.REMOTE_TO_LOCAL -> {
                        sftpChannel.downloadFileOrDir(hostDirectory, projectDirectory)
                    }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                sftpChannel.close()
            }
        }
    }

    override fun createBrowseListener(browser: DirectoryBrowser, host: ToolkitHost): ActionListener {
        val sshConfig = host.requireSshConfig()

        return ActionListener {
            val application = ApplicationManager.getApplication()
            val pathToExpand = browser.text.takeIf(String::isNotBlank)
            application.executeOnPooledThread {
                val channel = try {
                    connectionBuilder(sshConfig).openFailSafeSftpChannel()
                } catch (error: Exception) {
                    browser.showBrowseError(error)
                    return@executeOnPooledThread
                }

                application.invokeLater(
                    {
                        if (browser.project?.isDisposed == true) {
                            application.executeOnPooledThread(channel::close)
                            return@invokeLater
                        }
                        try {
                            val dialog = RemoteBrowserDialog(
                                remoteBrowserProvider = SftpRemoteBrowserProvider(channel),
                                project = browser.project,
                                foldersOnly = true,
                                hostName = sshConfig.presentableShortName,
                                pathToExpand = pathToExpand,
                                withCreateDirectoryButton = true,
                            )
                            if (dialog.showAndGet()) browser.text = dialog.getResult()
                        } catch (error: Exception) {
                            browser.showBrowseError(error)
                        } finally {
                            application.executeOnPooledThread(channel::close)
                        }
                    },
                    ModalityState.any(),
                )
            }
        }
    }

    override suspend fun resolveDefaultWorkingDirectory(project: Project, host: ToolkitHost): String {
        val sshConfig = host.requireSshConfig()
        return runInterruptible(Dispatchers.IO) {
            connectionBuilder(sshConfig)
                .openFailSafeSftpChannel()
                .use { channel ->
                    val workspaceRoot = joinRemotePath(channel.home, ".xmake")
                    joinRemotePath(workspaceRoot, project.locationHash)
                }
        }
    }

    override fun startProcess(host: ToolkitHost, command: GeneralCommandLine): Process {
        val sshConfig = host.requireSshConfig()
        Log.info("commandOnRemote: ${command.commandLineString}")
        return connectionBuilder(sshConfig)
            .processBuilder(command)
            .withAllocatePty(false)
            .start()
    }

    private fun connectionBuilder(sshConfig: SshConfig): ConnectionBuilder =
        ConnectionBuilder(sshConfig.host)
            .withSshPasswordProvider(PlatformSshPasswordProvider(sshConfig.copyToCredentials()))

    private fun ToolkitHost.requireSshConfig(): SshConfig =
        sshConfig
            ?: error("SSH host backend is not available: ${backendId.orEmpty()}")

    private fun DirectoryBrowser.showBrowseError(error: Exception) {
        Log.warn("Failed to open the SSH directory browser", error)
        ApplicationManager.getApplication().invokeLater(
            {
                if (project?.isDisposed != true) {
                    Messages.showErrorDialog(
                        project,
                        error.message ?: "Unable to open the SSH directory browser",
                        "SSH Directory Browser",
                    )
                }
            },
            ModalityState.any(),
        )
    }

    private fun joinRemotePath(parent: String, child: String): String =
        if (parent == "/") "/$child" else "${parent.trimEnd('/')}/$child"

    /** Removes remote entries without a local counterpart so uploads mirror the local project. */
    private fun SftpChannel.pruneMissingEntries(
        localRoot: File,
        remoteRoot: String,
        checkCanceled: () -> Unit,
    ) {
        val entries = try {
            ls(remoteRoot)
        } catch (_: SftpChannelNoSuchFileException) {
            return
        }
        entries.forEach { entry ->
            checkCanceled()
            val name = entry.name
            if (name == "." || name == ".." || name in defaultSyncExcludedEntryNames) return@forEach
            val localFile = File(localRoot, name)
            val remotePath = joinRemotePath(remoteRoot, name)
            when {
                !entry.attrs.isDir -> if (!localFile.isFile) rm(remotePath)
                localFile.isDirectory -> pruneMissingEntries(localFile, remotePath, checkCanceled)
                else -> deleteRemoteTree(remotePath, checkCanceled)
            }
        }
    }

    private fun SftpChannel.deleteRemoteTree(remotePath: String, checkCanceled: () -> Unit) {
        val entries = try {
            ls(remotePath)
        } catch (_: SftpChannelNoSuchFileException) {
            return
        }
        entries.forEach { entry ->
            checkCanceled()
            val name = entry.name
            if (name == "." || name == "..") return@forEach
            val childPath = joinRemotePath(remotePath, name)
            if (entry.attrs.isDir) deleteRemoteTree(childPath, checkCanceled) else rm(childPath)
        }
        rmdir(remotePath)
    }

    companion object {
        private val Log = logger<SshToolkitHostExtensionImpl>()
    }
}
