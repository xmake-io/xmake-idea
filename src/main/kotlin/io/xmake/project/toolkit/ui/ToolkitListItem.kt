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

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.actionSystem.AnAction
import io.xmake.icons.XMakeIcons
import io.xmake.project.toolkit.Toolkit
import javax.swing.Icon

open class ToolkitListItem(
    val id: String,
    var text: String?,
    var secondaryText: String? = null,
    var tertiaryText: String? = null,
    var caption: String? = null,
    var isCaptionVisible: Boolean = false,
    var icon: Icon? = null,
) {

    infix operator fun compareTo(other: ToolkitListItem): Int {
        return if (this is ToolkitItem && other is ToolkitItem) {
            return this compareTo other
        } else if (this is NoneItem || other is NoneItem) {
            compareValuesBy(this, other) { it.id }
        } else {
            return compareValuesBy(this, other) { it.text }
        }
    }

    class NoneItem : ToolkitListItem(id = "", text = "None")

    open class ToolkitItem(val toolkit: Toolkit) : ToolkitListItem(
        toolkit.id,
        toolkit.path,
        toolkit.name,
        toolkit.version,
        toolkit.host.type.name,
        true,
        XMakeIcons.XMAKE
    ) {
        infix operator fun compareTo(other: ToolkitItem): Int {
            return compareValuesBy(this, other,
                { if (it.caption == "Registered") -1 else it.toolkit.host.type.ordinal },
                { it.toolkit.host.type.ordinal },
                { it.toolkit.path }
            )
        }

        fun asRegistered(): ToolkitItem {
            if (this.toolkit.isRegistered)
                return this.apply { caption = "Registered" }
            else
                throw RuntimeConfigurationError("Toolkit is not registered!")
        }

        fun asInvalid(): ToolkitItem {
            return this.apply { tertiaryText = "Invalid" }
        }

        fun asCurrent(): ToolkitItem {
            return this.apply { caption = "Current" }
        }
    }

    enum class ActionRole { DOWNLOAD, ADD }

    class ActionItem(
        id: String,
        name: String,
        private val role: ActionRole,
        private val action: AnAction,
    ) : ToolkitListItem(id, name,) {}
}