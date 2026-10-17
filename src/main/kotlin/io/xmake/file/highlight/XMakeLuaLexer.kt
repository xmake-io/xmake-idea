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
 * @file        XMakeLuaLexer.kt
 *
 */
package io.xmake.file.highlight

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

class XMakeLuaLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var currentState = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        this.currentState = initialState
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = currentState
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        if (tokenStart >= endOffset) {
            tokenType = null
            return
        }

        val c = buffer[tokenStart]

        // Whitespace
        if (Character.isWhitespace(c)) {
            tokenEnd++
            while (tokenEnd < endOffset && Character.isWhitespace(buffer[tokenEnd])) {
                tokenEnd++
            }
            tokenType = XMakeLuaTokenTypes.WHITE_SPACE
            return
        }

        // Comment -- or --[=*[ ... ]=*]
        if (c == '-' && tokenStart + 1 < endOffset && buffer[tokenStart + 1] == '-') {
            val level = longBracketLevelAt(tokenStart + 2)

            if (level >= 0) {
                tokenEnd = scanLongBracketClose(tokenStart + 4 + level, level)
            } else {
                // Short comment
                tokenEnd += 2
                while (tokenEnd < endOffset && buffer[tokenEnd] != '\n') {
                    tokenEnd++
                }
            }
            tokenType = XMakeLuaTokenTypes.COMMENT
            return
        }

        // Long String [=*[ ... ]=*]
        if (c == '[') {
            val level = longBracketLevelAt(tokenStart)
            if (level >= 0) {
                tokenEnd = scanLongBracketClose(tokenStart + 2 + level, level)
                tokenType = XMakeLuaTokenTypes.STRING
                return
            }
        }

        // String " or '
        if (c == '"' || c == '\'') {
            val quote = c
            tokenEnd++
            while (tokenEnd < endOffset) {
                if (buffer[tokenEnd] == '\\') {
                    tokenEnd = (tokenEnd + 2).coerceAtMost(endOffset)
                    continue
                }
                if (buffer[tokenEnd] == quote) {
                    tokenEnd++
                    break
                }
                tokenEnd++
            }
            tokenType = XMakeLuaTokenTypes.STRING
            return
        }

        // Number
        if (Character.isDigit(c) || (c == '.' && tokenStart + 1 < endOffset && Character.isDigit(buffer[tokenStart + 1]))) {
             if (c == '.') {
                 tokenEnd++
                 while (tokenEnd < endOffset && Character.isDigit(buffer[tokenEnd])) {
                     tokenEnd++
                 }
             } else if (c == '0' && tokenStart + 1 < endOffset && (buffer[tokenStart+1] == 'x' || buffer[tokenStart+1] == 'X')) {
                 // Hex
                 tokenEnd += 2
                 while (tokenEnd < endOffset && isHexDigit(buffer[tokenEnd])) {
                     tokenEnd++
                 }
                 // Hex exponent p/P
                 if (tokenEnd < endOffset && (buffer[tokenEnd] == 'p' || buffer[tokenEnd] == 'P')) {
                     tokenEnd++
                     if (tokenEnd < endOffset && (buffer[tokenEnd] == '+' || buffer[tokenEnd] == '-')) {
                         tokenEnd++
                     }
                     while (tokenEnd < endOffset && Character.isDigit(buffer[tokenEnd])) {
                         tokenEnd++
                     }
                 }
                 tokenType = XMakeLuaTokenTypes.NUMBER
                 return
             } else {
                 // Decimal
                 tokenEnd++
                 while (tokenEnd < endOffset && Character.isDigit(buffer[tokenEnd])) {
                     tokenEnd++
                 }
                 // Fraction
                 if (tokenEnd < endOffset && buffer[tokenEnd] == '.') {
                      if (tokenEnd + 1 < endOffset && buffer[tokenEnd+1] == '.') {
                          // concat, stop
                      } else {
                          tokenEnd++
                          while (tokenEnd < endOffset && Character.isDigit(buffer[tokenEnd])) {
                              tokenEnd++
                          }
                      }
                 }
             }
             // Exponent e/E (common for decimal and .123)
             if (tokenEnd < endOffset && (buffer[tokenEnd] == 'e' || buffer[tokenEnd] == 'E')) {
                 tokenEnd++
                 if (tokenEnd < endOffset && (buffer[tokenEnd] == '+' || buffer[tokenEnd] == '-')) {
                     tokenEnd++
                 }
                 while (tokenEnd < endOffset && Character.isDigit(buffer[tokenEnd])) {
                     tokenEnd++
                 }
             }
             tokenType = XMakeLuaTokenTypes.NUMBER
             return
        }

        // Identifier or Keyword
        if (isIdentifierStart(c)) {
            tokenEnd++
            while (tokenEnd < endOffset && isIdentifierPart(buffer[tokenEnd])) {
                tokenEnd++
            }
            val text = buffer.subSequence(tokenStart, tokenEnd).toString()
            if (LUA_KEYWORDS.contains(text)) {
                tokenType = XMakeLuaTokenTypes.KEYWORD
            } else if (XMakeApiRegistry.current.contains(text)) {
                tokenType = XMakeLuaTokenTypes.XMAKE_API
            } else {
                tokenType = XMakeLuaTokenTypes.IDENTIFIER
            }
            return
        }

        // Operator / Symbols
        tokenEnd++
        tokenType = XMakeLuaTokenTypes.OPERATOR
    }

    private fun longBracketLevelAt(pos: Int): Int {
        if (pos >= endOffset || buffer[pos] != '[') return -1
        var i = pos + 1
        while (i < endOffset && buffer[i] == '=') {
            i++
        }
        return if (i < endOffset && buffer[i] == '[') i - pos - 1 else -1
    }

    private fun scanLongBracketClose(from: Int, level: Int): Int {
        var i = from
        while (i < endOffset) {
            if (buffer[i] == ']') {
                var j = i + 1
                var equals = 0
                while (j < endOffset && buffer[j] == '=') {
                    equals++
                    j++
                }
                if (j < endOffset && buffer[j] == ']' && equals == level) {
                    return j + 1
                }
            }
            i++
        }
        return endOffset
    }

    private fun isHexDigit(c: Char): Boolean {
        return Character.isDigit(c) || (c in 'a'..'f') || (c in 'A'..'F')
    }

    private fun isIdentifierStart(c: Char): Boolean {
        return Character.isLetter(c) || c == '_'
    }

    private fun isIdentifierPart(c: Char): Boolean {
        return Character.isLetterOrDigit(c) || c == '_'
    }

    companion object {
        val LUA_KEYWORDS = setOf(
            "and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "if", "in", "local", "nil", "not", "or", "repeat",
            "return", "then", "true", "until", "while", "goto"
        )

    }
}
