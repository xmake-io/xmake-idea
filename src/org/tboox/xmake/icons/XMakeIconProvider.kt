package org.tboox.xmake.icons

import com.intellij.ide.IconProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import javax.swing.Icon

class XMakeIconProvider : IconProvider() {

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        Log.info("getIcon")
        return null
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(XMakeIconProvider::class.java.getName())
    }

}
