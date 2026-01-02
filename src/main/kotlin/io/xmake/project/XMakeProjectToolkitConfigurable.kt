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
 * @file        XMakeProjectToolkitConfigurable.kt
 *
 */
package io.xmake.project

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.project.toolkit.ui.ToolkitListItem
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList

class XMakeProjectToolkitConfigurable : Configurable, Configurable.NoScroll {

    private val toolkitManager = ToolkitManager.getInstance()

    override fun createComponent(): JComponent {
        val registeredToolkit = toolkitManager.getRegisteredToolkits()
        val listModel = DefaultListModel<ToolkitListItem>().apply { registeredToolkit.forEach {
            addElement(ToolkitListItem.ToolkitItem(it).asRegistered()) }
        }
        val toolkitList = JBList(listModel).apply {

            cellRenderer = object : ColoredListCellRenderer<ToolkitListItem>() {
                override fun customizeCellRenderer(
                    list: JList<out ToolkitListItem>,
                    value: ToolkitListItem?,
                    index: Int,
                    selected: Boolean,
                    hasFocus: Boolean,
                ) {

                    isOpaque = false
                    icon = value?.icon
                    val text = value?.let { value.text } ?: ""
                    val secondaryText = value?.secondaryText
                    val tertiaryText = value?.tertiaryText

                    append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                    if (secondaryText != null)
                        append(" - $secondaryText ", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
                    if (tertiaryText != null)
                        append(" $tertiaryText", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }
        val decorator = ToolbarDecorator.createDecorator(toolkitList)

        decorator.setRemoveAction {
            val toolkit = (toolkitList.selectedValue as ToolkitListItem.ToolkitItem).toolkit
            if (Messages.showYesNoDialog(
                    "Unregister ${toolkit.name}?",
                    "Unregister Toolkit",
                    Messages.getQuestionIcon()
                ) == Messages.YES
            ) {
                toolkitManager.unregisterToolkit(toolkit)
                listModel.removeElement(toolkitList.selectedValue)
            }
        }

        return panel {
            row {
                cell(decorator.createPanel()).align(Align.FILL)
            }
        }
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun apply() {

    }

    override fun getDisplayName(): String {
        return "Xmake Project Toolkit"
    }

}