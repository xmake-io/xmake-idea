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