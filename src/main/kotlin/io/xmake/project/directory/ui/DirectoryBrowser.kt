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
import io.xmake.project.toolkit.ToolkitHostType.LOCAL
import io.xmake.project.toolkit.ToolkitHostType.SSH
import io.xmake.project.toolkit.ToolkitHostType.WSL
import io.xmake.utils.extension.ToolkitHostExtension
import java.awt.event.ActionListener

class DirectoryBrowser(
    val project: Project?,
    private val browseTitle: String = "Working Directory",
    private val browseDescription: String = "Select the working directory",
) : TextFieldWithBrowseButton() {

    private val listeners = mutableSetOf<ActionListener>()

    fun setToolkit(toolkit: Toolkit?) {
        removeBrowseListeners()
        setButtonEnabled(false)
        toolkit?.let { addBrowseListener(it.host) }
    }

    private fun createLocalBrowseListener(): ActionListener {
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        return BrowseFolderActionListener(
            this,
            project,
            fileChooserDescriptor
                .withTitle(browseTitle)
                .withDescription(browseDescription),
            TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
        )
    }

    private fun createWslBrowseListener(distribution: WSLDistribution): ActionListener {
        val fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
            .withTitle(browseTitle)
            .withDescription(browseDescription)
        return ActionListener {
            browseWslPath(
                this,
                distribution,
                this,
                true,
                fileChooserDescriptor,
            )
        }
    }

    private fun addBrowseListener(host: ToolkitHost) {
        val listener = when (host.type) {
            LOCAL -> createLocalBrowseListener()

            WSL -> {
                val distribution = host.wslDistribution ?: return
                createWslBrowseListener(distribution)
            }

            SSH -> {
                if (!host.hasBackend) return
                val extension = ToolkitHostExtension.forHostType(host.type) ?: return
                extension.createBrowseListener(this, host)
            }
        }

        addActionListener(listener)
        listeners += listener
        setButtonEnabled(true)
        Log.debug("Added directory browser listener for ${host.type}")
    }

    private fun removeBrowseListeners() {
        listeners.forEach(::removeActionListener)
        listeners.clear()
    }

    private companion object {
        private val Log = logger<DirectoryBrowser>()
    }
}
