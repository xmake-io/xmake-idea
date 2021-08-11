package org.tboox.xmake.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object XMakeIcons {

    // logo icon
    val XMAKE = load("/icons/xmake.png")

    // file icon
    val FILE = load("/icons/xmake.png")

    // error icon
    val ERROR = load("/icons/error.png")

    // warning icon
    val WARNING = load("/icons/warning.png")

    private fun load(path: String): Icon = IconLoader.getIcon(path, XMakeIcons::class.java)

}
