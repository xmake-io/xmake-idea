package io.xmake.project.directory.ui

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.ui.browseWslPath
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.utils.extension.ToolkitHostExtension
import java.awt.event.ActionListener

class DirectoryBrowser(val project: Project?) : TextFieldWithBrowseButton() {

    private val listeners = mutableSetOf<ActionListener>()

    private val EP_NAME: ExtensionPointName<ToolkitHostExtension> = ExtensionPointName("io.xmake.toolkitHostExtension")

    private fun createLocalBrowseListener(): ActionListener {
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        val browseFolderListener = BrowseFolderActionListener(
            this,
            project,
            fileChooserDescriptor
                .withTitle("Working Directory")
                .withDescription("Select the working directory"),
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

    fun addBrowserListenerByToolkit(toolkit: Toolkit){
        addBrowserListenerByHostType(toolkit.host)
    }

    fun addBrowserListenerByHostType(host: ToolkitHost) {
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
                EP_NAME.extensions.first { it.KEY == "SSH" }.let { extension ->
                    println("host: $host")
                    val browseListener = with(extension) { createBrowseListener(host) }
                    addActionListener(browseListener)
                    listeners.add(browseListener)
                    Log.debug("addActionListener ${extension.getHostType()}: $browseListener")
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

