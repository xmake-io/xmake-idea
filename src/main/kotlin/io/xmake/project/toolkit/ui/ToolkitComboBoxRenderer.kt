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
 * @file        ToolkitComboBoxRenderer.kt
 *
 */
package io.xmake.project.toolkit.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.asSequence
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList

class ToolkitComboBoxRenderer(component: JComponent) : GroupedComboBoxRenderer<ToolkitListItem?>(component) {

    override fun isSeparatorVisible(list: JList<out ToolkitListItem?>?, value: ToolkitListItem?): Boolean {
        return list?.model?.asSequence()?.firstOrNull { it?.caption == value?.caption } == value
    }

    override fun separatorFor(value: ToolkitListItem?): ListSeparator {
        return ListSeparator(value?.caption ?: "")
    }

    override fun getText(item: ToolkitListItem?): String {
        return item?.text ?: ""
    }

    override fun getSecondaryText(item: ToolkitListItem?): String? {
        return item?.secondaryText
    }

    override fun getIcon(item: ToolkitListItem?): Icon {
        return item?.icon ?: AllIcons.General.Error
    }

    override fun getCaption(list: JList<out ToolkitListItem?>?, value: ToolkitListItem?): String? {
        return if (value?.isCaptionVisible == true) value.caption else null
    }

    override fun customize(
        item: SimpleColoredComponent,
        value: ToolkitListItem?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ) {
        item.icon = getIcon(value)
        val text = getText(value)
        val secondaryText = getSecondaryText(value)
        val tertiaryText = value?.tertiaryText

        item.append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        if (secondaryText != null)
            item.append(" - $secondaryText ", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
        if (tertiaryText != null)
            item.append(" $tertiaryText", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
}
