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
 * @file        XMakeLuaSyntaxHighlighter.kt
 *
 */
package io.xmake.file.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey

class XMakeLuaSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer {
        return XMakeLuaLexer()
    }

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            XMakeLuaTokenTypes.KEYWORD -> KEYWORD_KEYS
            XMakeLuaTokenTypes.XMAKE_API -> XMAKE_API_KEYS
            XMakeLuaTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS
            XMakeLuaTokenTypes.NUMBER -> NUMBER_KEYS
            XMakeLuaTokenTypes.STRING -> STRING_KEYS
            XMakeLuaTokenTypes.COMMENT -> COMMENT_KEYS
            XMakeLuaTokenTypes.OPERATOR -> OPERATOR_KEYS
            XMakeLuaTokenTypes.BAD_CHARACTER -> BAD_CHAR_KEYS
            else -> EMPTY_KEYS
        }
    }

    companion object {
        val KEYWORD = createTextAttributesKey("XMAKE_LUA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val XMAKE_API = createTextAttributesKey("XMAKE_LUA_API", DefaultLanguageHighlighterColors.KEYWORD)
        val IDENTIFIER = createTextAttributesKey("XMAKE_LUA_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val NUMBER = createTextAttributesKey("XMAKE_LUA_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val STRING = createTextAttributesKey("XMAKE_LUA_STRING", DefaultLanguageHighlighterColors.STRING)
        val COMMENT = createTextAttributesKey("XMAKE_LUA_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val OPERATOR = createTextAttributesKey("XMAKE_LUA_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val BAD_CHARACTER = createTextAttributesKey("XMAKE_LUA_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val KEYWORD_KEYS = arrayOf(KEYWORD)
        private val XMAKE_API_KEYS = arrayOf(XMAKE_API)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val STRING_KEYS = arrayOf(STRING)
        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val OPERATOR_KEYS = arrayOf(OPERATOR)
        private val BAD_CHAR_KEYS = arrayOf(BAD_CHARACTER)
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }
}
