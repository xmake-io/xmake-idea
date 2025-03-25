package io.xmake.file

import com.intellij.lang.Language

object XMakeLuaLanguage : Language("xmake.lua") {
    private fun readResolve(): Any = XMakeLuaLanguage
    val INSTANCE: XMakeLuaLanguage = XMakeLuaLanguage
}