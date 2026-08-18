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
 * @file        ToolkitListItem.kt
 *
 */
package io.xmake.project.toolkit.ui

import io.xmake.icons.XMakeIcons
import io.xmake.project.toolkit.Toolkit
import javax.swing.Icon

sealed class ToolkitListItem(
    val id: String,
    val text: String?,
    val secondaryText: String? = null,
    val tertiaryText: String? = null,
    val caption: String? = null,
    val isCaptionVisible: Boolean = false,
    val icon: Icon? = null,
) : Comparable<ToolkitListItem> {

    override fun compareTo(other: ToolkitListItem): Int = when {
        this is None && other is None -> 0
        this is None -> -1
        other is None -> 1
        else -> compareValuesBy(this, other, ToolkitListItem::id)
    }

    data object None : ToolkitListItem(id = "", text = "None")

    class Entry(
        val toolkit: Toolkit,
    ) : ToolkitListItem(
        id = toolkit.id,
        text = toolkit.path,
        secondaryText = toolkit.name,
        tertiaryText = if (!toolkit.isAvailable) {
            "Unavailable"
        } else {
            toolkit.version
        },
        caption = if (toolkit.isRegistered) "Registered" else toolkit.host.type.name,
        isCaptionVisible = true,
        icon = XMakeIcons.XMAKE,
    ) {
        override fun compareTo(other: ToolkitListItem): Int = when (other) {
            is None -> 1
            is Entry -> compareValuesBy(
                this,
                other,
                { entry -> !entry.toolkit.isRegistered },
                { entry -> entry.toolkit.host.type },
                { entry -> entry.toolkit.host.id },
                { entry -> entry.toolkit.path },
                Entry::id,
            )
        }
    }
}
