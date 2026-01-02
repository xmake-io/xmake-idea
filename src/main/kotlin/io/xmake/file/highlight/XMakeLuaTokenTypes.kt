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
 * @file        XMakeLuaTokenTypes.kt
 *
 */
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
