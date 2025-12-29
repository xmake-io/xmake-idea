package io.xmake.file.highlight

import com.intellij.psi.tree.IElementType
import com.intellij.psi.TokenType
import io.xmake.file.XMakeLuaLanguage

object XMakeLuaTokenTypes {
    class XMakeLuaElementType(debugName: String) : IElementType(debugName, XMakeLuaLanguage.INSTANCE)

    val KEYWORD = XMakeLuaElementType("KEYWORD")      // Lua keywords (if, while...)
    val XMAKE_API = XMakeLuaElementType("XMAKE_API")  // XMake APIs (target, add_deps...)
    val IDENTIFIER = XMakeLuaElementType("IDENTIFIER")
    val NUMBER = XMakeLuaElementType("NUMBER")
    val STRING = XMakeLuaElementType("STRING")
    val COMMENT = XMakeLuaElementType("COMMENT")
    val OPERATOR = XMakeLuaElementType("OPERATOR")
    val BAD_CHARACTER = TokenType.BAD_CHARACTER
    val WHITE_SPACE = TokenType.WHITE_SPACE
}
