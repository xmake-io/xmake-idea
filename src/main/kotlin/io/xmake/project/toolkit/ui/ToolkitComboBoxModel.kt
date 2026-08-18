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
 */
package io.xmake.project.toolkit.ui

import com.intellij.ui.SortedComboBoxModel
import io.xmake.project.toolkit.Toolkit
import kotlin.comparisons.naturalOrder

/** Projects the visible toolkit list into the stable items displayed by a ComboBox. */
internal class ToolkitComboBoxModel : SortedComboBoxModel<ToolkitListItem>(naturalOrder()) {
    init {
        add(ToolkitListItem.None)
    }

    fun synchronizeWith(visibleToolkits: List<Toolkit>, selectedToolkitId: String?): Boolean {
        val desiredItems = visibleToolkits.associateBy(Toolkit::id)
        val removed = removeObsoleteItems(desiredItems)
        val updated = updateItems(desiredItems)
        selectItem(selectedToolkitId)
        return removed || updated
    }

    private fun removeObsoleteItems(desiredItems: Map<String, Toolkit>): Boolean {
        val obsoleteItems = items
            .filterIsInstance<ToolkitListItem.Entry>()
            .filterNot { item -> item.id in desiredItems }
        obsoleteItems.forEach(::remove)
        return obsoleteItems.isNotEmpty()
    }

    private fun updateItems(desiredItems: Map<String, Toolkit>): Boolean {
        val currentItems = items
            .filterIsInstance<ToolkitListItem.Entry>()
            .associateBy(ToolkitListItem.Entry::id)
        var changed = false

        desiredItems.values.forEach { toolkit ->
            val currentItem = currentItems[toolkit.id]
            if (currentItem == null) {
                add(ToolkitListItem.Entry(toolkit))
                changed = true
            } else if (!currentItem.toolkit.isSameSnapshotAs(toolkit)) {
                val wasSelected = selectedItem === currentItem
                remove(currentItem)
                val replacement = ToolkitListItem.Entry(toolkit)
                add(replacement)
                if (wasSelected) selectedItem = replacement
                changed = true
            }
        }
        return changed
    }

    private fun selectItem(selectedToolkitId: String?) {
        val item = selectedToolkitId
            ?.let { id -> items.firstOrNull { item -> item.id == id } }
            ?: items.firstOrNull { item -> item is ToolkitListItem.None }
        if (selectedItem !== item) selectedItem = item
    }
}
