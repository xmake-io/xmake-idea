package io.xmake.project.directory.ui

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.ui.browseWslPath
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ssh.ConnectionBuilder
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ssh.interaction.PlatformSshPasswordProvider
import com.intellij.ssh.ui.sftpBrowser.RemoteBrowserDialog
import com.intellij.ssh.ui.sftpBrowser.SftpRemoteBrowserProvider
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.awt.event.ActionListener


class DirectoryBrowser(val project: Project) : TextFieldWithBrowseButton() {

    private val listeners = mutableSetOf<ActionListener>()

    private fun createLocalBrowseListener(): ActionListener {
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        val browseFolderListener = BrowseFolderActionListener(
            "Working Directory",
            null,
            this,
            project,
            fileChooserDescriptor,
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT
        )
        return browseFolderListener
    }

    private fun createWslBrowseListener(target: WSLDistribution): ActionListener {
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        val wslBrowseFolderListener = ActionListener {
            browseWslPath(this,
                target,
                this,
                true,
                fileChooserDescriptor)
        }
        return wslBrowseFolderListener
    }

    private fun createSftpBrowseListener(target: SshConfig): ActionListener {
        val sftpChannel = runBlocking(Dispatchers.Default){
            ConnectionBuilder(target.host)
                .withSshPasswordProvider(PlatformSshPasswordProvider(target.copyToCredentials()))
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

    fun addBrowserListenerByToolkit(toolkit: Toolkit){
        with(toolkit){
            when (host.type) {
                LOCAL -> {
                    val localBrowseListener = createLocalBrowseListener()
                    addActionListener(localBrowseListener)
                    listeners.add(localBrowseListener)
                    Log.debug("addActionListener local: $localBrowseListener")
                }
                WSL -> {
                    val wslBrowseListener = createWslBrowseListener(host.target as WSLDistribution)
                    addActionListener(wslBrowseListener)
                    listeners.add(wslBrowseListener)
                    Log.debug("addActionListener wsl: $wslBrowseListener")
                }
                SSH -> {
                    val sftpBrowseListener = createSftpBrowseListener(host.target as SshConfig)
                    addActionListener(sftpBrowseListener)
                    listeners.add(sftpBrowseListener)
                    Log.debug("addActionListener remote: $sftpBrowseListener")
                }
            }
        }
    }

    fun removeBrowserAllListener() {
        listeners.onEach {
            removeActionListener(it)
        }.clear()
    }
    companion object{
        private val Log = logger<DirectoryBrowser>()
    }
}


