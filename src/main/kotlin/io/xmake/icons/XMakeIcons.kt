package io.xmake.icons

import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object XMakeIcons {

    // logo icon
    val XMAKE = load("/icons/xmake.svg")

    // file icon
    val FILE = load("/icons/xmake.svg")

    // error icon
    val ERROR = ExpUiIcons.Status.Error

    // warning icon
    val WARNING = ExpUiIcons.Status.Warning

    private fun load(path: String): Icon = IconLoader.getIcon(path, XMakeIcons::class.java)

}
