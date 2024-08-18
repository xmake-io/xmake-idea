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

    private fun getTertiaryText(item: ToolkitListItem?): String? {
        return item?.tertiaryText
    }

    override fun getIcon(item: ToolkitListItem?): Icon {
        return item?.icon?: AllIcons.General.Error
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
        val text = value?.let { getText(value) } ?: ""
        val secondaryText = getSecondaryText(value)
        val tertiaryText = getTertiaryText(value)

        item.append(text, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        if (secondaryText != null)
            item.append(" - $secondaryText ", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
        if (tertiaryText != null)
            item.append(" $tertiaryText", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
}

