package org.tboox.xmake.lang

import com.intellij.lang.Language

object XMakeLanguage : Language("XMake", "text/xmake", "text/x-xmake", "application/x-xmake") {
    override fun isCaseSensitive() = true
    override fun getDisplayName() = "XMake"
}

