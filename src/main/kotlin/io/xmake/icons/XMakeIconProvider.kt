package io.xmake.icons

import com.intellij.ide.IconProvider
import com.intellij.psi.PsiElement
import javax.swing.Icon

class XMakeIconProvider : IconProvider() {

    override fun getIcon(element: PsiElement, flags: Int): Icon? = when {
        element.containingFile?.name == "xmake.lua" -> XMakeIcons.FILE
        else -> null
    }
}
