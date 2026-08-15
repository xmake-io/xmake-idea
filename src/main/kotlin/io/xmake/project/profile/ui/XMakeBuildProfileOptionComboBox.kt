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
package io.xmake.project.profile.ui

import com.intellij.ui.MutableCollectionComboBoxModel
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.utils.ui.LiveModelComboBox

internal class XMakeBuildProfileOptionComboBox : LiveModelComboBox<String>(MutableCollectionComboBoxModel()) {
    private val optionModel: MutableCollectionComboBoxModel<String>
        get() = model as MutableCollectionComboBoxModel<String>

    init {
        maximumRowCount = 30
    }

    fun updateOptions(
        values: Iterable<String>,
        selectedValue: String?,
        fallback: String = XMakeBuildProfile.USE_XMAKE_DEFAULT,
    ) {
        val currentValue = selectedValue.orEmpty().ifBlank { fallback }
        val options = buildList {
            add(fallback)
            addAll(values.filter(String::isNotBlank))
            if (currentValue !in this) add(currentValue)
        }.distinct()

        synchronizeOptions(options)
        if (selectedItem != currentValue) selectedItem = currentValue
        refreshPopupFromModel()
    }

    private fun synchronizeOptions(options: List<String>) {
        val commonSize = minOf(optionModel.size, options.size)
        for (index in 0 until commonSize) {
            if (optionModel.getElementAt(index) != options[index]) {
                optionModel.setElementAt(options[index], index)
            }
        }
        when {
            optionModel.size > options.size ->
                // IntelliJ's removeRange is inclusive on both ends, so this removes the tail after `options`.
                optionModel.removeRange(options.size, optionModel.size - 1)
            optionModel.size < options.size ->
                optionModel.add(options.drop(optionModel.size))
        }
    }
}
