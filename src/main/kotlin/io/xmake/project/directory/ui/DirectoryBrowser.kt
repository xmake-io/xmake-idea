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
 * @file        DirectoryBrowser.kt
 *
 */
package io.xmake.project.directory.ui

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.ui.browseWslPath
import com.intellij.openapi.diagnostic.logger
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

    private fun createWslBrowseListener(distribution: WSLDistribution): ActionListener {
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        val wslBrowseFolderListener = ActionListener {
            browseWslPath(this,
                distribution,
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
                val distribution = host.wslDistribution ?: return
                val wslBrowseListener = createWslBrowseListener(distribution)
                addActionListener(wslBrowseListener)
                listeners.add(wslBrowseListener)
                Log.debug("addActionListener wsl: $wslBrowseListener")
            }

            SSH -> {
                val extension = ToolkitHostExtension.forHostType(SSH) ?: return
                val browseListener = extension.createBrowseListener(this, host)
                addActionListener(browseListener)
                listeners.add(browseListener)
                Log.debug("addActionListener SSH: $browseListener")
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
