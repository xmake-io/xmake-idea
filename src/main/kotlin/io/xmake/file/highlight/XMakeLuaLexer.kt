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
            var isLong = false
            var equalsCount = 0
            if (tokenStart + 2 < endOffset && buffer[tokenStart + 2] == '[') {
                var i = tokenStart + 3
                while (i < endOffset && buffer[i] == '=') {
                    equalsCount++
                    i++
                }
                if (i < endOffset && buffer[i] == '[') {
                    isLong = true
                }
            }

            if (isLong) {
                // --[=*[
                tokenEnd = tokenStart + 4 + equalsCount
                while (tokenEnd < endOffset) {
                    if (buffer[tokenEnd] == ']') {
                        var closeEquals = 0
                        var j = tokenEnd + 1
                        while (j < endOffset && buffer[j] == '=') {
                            closeEquals++
                            j++
                        }
                        if (j < endOffset && buffer[j] == ']' && closeEquals == equalsCount) {
                            tokenEnd = j + 1
                            break
                        }
                    }
                    tokenEnd++
                }
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
            var equalsCount = 0
            var i = tokenStart + 1
            while (i < endOffset && buffer[i] == '=') {
                equalsCount++
                i++
            }
            if (i < endOffset && buffer[i] == '[') {
                tokenEnd = i + 1
                while (tokenEnd < endOffset) {
                    if (buffer[tokenEnd] == ']') {
                        var closeEquals = 0
                        var j = tokenEnd + 1
                        while (j < endOffset && buffer[j] == '=') {
                            closeEquals++
                            j++
                        }
                        if (j < endOffset && buffer[j] == ']' && closeEquals == equalsCount) {
                            tokenEnd = j + 1
                            break
                        }
                    }
                    tokenEnd++
                }
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
                    tokenEnd += 2
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
                 // Check if it is .. (concat)
                 if (tokenStart + 1 < endOffset && buffer[tokenStart + 1] == '.') {
                     // .. or ...
                     tokenEnd += 2
                     if (tokenEnd < endOffset && buffer[tokenEnd] == '.') {
                         tokenEnd++
                     }
                     tokenType = XMakeLuaTokenTypes.OPERATOR
                     return
                 }
                 // It is .123
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
            } else if (XMAKE_APIS.contains(text)) {
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

        @Volatile
        var XMAKE_APIS = setOf(
            "add_arflags", "add_asflags", "add_bindirs", "add_cflags", "add_cfuncs",
            "add_cincludes", "add_cleanfiles", "add_components", "add_configfiles", "add_configs",
            "add_csnippets", "add_ctypes", "add_cuflags", "add_cugencodes", "add_culdflags",
            "add_cxflags", "add_cxxflags", "add_cxxfuncs", "add_cxxincludes", "add_cxxsnippets",
            "add_cxxtypes", "add_dcflags", "add_defines", "add_deps", "add_embeddirs",
            "add_extrafiles", "add_extsources", "add_fcflags", "add_features", "add_filegroups",
            "add_files", "add_forceincludes", "add_frameworkdirs", "add_frameworks", "add_gcflags",
            "add_headerfiles", "add_imports", "add_includedirs", "add_installfiles", "add_kcflags",
            "add_languages", "add_ldflags", "add_linkdirs", "add_linkgroups", "add_linkorders",
            "add_links", "add_mflags", "add_moduledirs", "add_mrcflags", "add_mxflags",
            "add_mxxflags", "add_ncflags", "add_options", "add_orders", "add_packagedirs",
            "add_packages", "add_patches", "add_pcflags", "add_platformdirs", "add_plugindirs",
            "add_rcflags", "add_repositories", "add_requireconfs", "add_requires", "add_resources",
            "add_rpathdirs", "add_rules", "add_runenvs", "add_scflags", "add_shflags",
            "add_sysincludedirs", "add_syslinks", "add_tests", "add_toolchaindirs", "add_toolchains",
            "add_toolset", "add_undefines", "add_urls", "add_values", "add_vectorexts",
            "add_versionfiles", "add_versions", "add_zcflags", "after_build", "after_build_file",
            "after_build_files", "after_buildcmd", "after_buildcmd_file", "after_buildcmd_files", "after_check",
            "after_clean", "after_config", "after_install", "after_installcmd", "after_link",
            "after_linkcmd", "after_load", "after_package", "after_prepare", "after_prepare_file",
            "after_prepare_files", "after_preparecmd", "after_preparecmd_file", "after_preparecmd_files", "after_run",
            "after_test", "after_uninstall", "after_uninstallcmd", "assert", "before_build",
            "before_build_file", "before_build_files", "before_buildcmd", "before_buildcmd_file", "before_buildcmd_files",
            "before_check", "before_clean", "before_config", "before_install", "before_installcmd",
            "before_link", "before_linkcmd", "before_load", "before_package", "before_prepare",
            "before_prepare_file", "before_prepare_files", "before_preparecmd", "before_preparecmd_file", "before_preparecmd_files",
            "before_run", "before_test", "before_uninstall", "before_uninstallcmd", "catch",
            "cprint", "cprintf", "del_files", "dprint", "dprintf",
            "finally", "find_package", "find_packages", "format", "get_config",
            "getenv", "has_config", "has_package", "import", "includes",
            "inherit", "ipairs", "irpairs", "is_arch", "is_config",
            "is_cross", "is_host", "is_kind", "is_mode", "is_os",
            "is_plat", "is_subhost", "on_build", "on_build_file", "on_build_files",
            "on_buildcmd", "on_buildcmd_file", "on_buildcmd_files", "on_check", "on_clean",
            "on_component", "on_config", "on_download", "on_fetch", "on_install",
            "on_installcmd", "on_link", "on_linkcmd", "on_load", "on_package",
            "on_prepare", "on_prepare_file", "on_prepare_files", "on_preparecmd", "on_preparecmd_file",
            "on_preparecmd_files", "on_run", "on_source", "on_test", "on_uninstall",
            "on_uninstallcmd", "option", "package", "pairs", "print",
            "printf", "raise", "remove_configfiles", "remove_extrafiles", "remove_files",
            "remove_headerfiles", "remove_installfiles", "rule", "set_allowedarchs", "set_allowedmodes",
            "set_allowedplats", "set_arch", "set_archs", "set_autogendir", "set_base",
            "set_basename", "set_bindir", "set_cachedir", "set_category", "set_config",
            "set_configdir", "set_configvar", "set_cross", "set_default", "set_defaultarchs",
            "set_defaultmode", "set_defaultplat", "set_dependir", "set_description", "set_enabled",
            "set_encodings", "set_exceptions", "set_extension", "set_extensions", "set_filename",
            "set_formats", "set_fpmodels", "set_group", "set_homepage", "set_installdir",
            "set_installtips", "set_kind", "set_languages", "set_license", "set_objectdir",
            "set_optimize", "set_options", "set_parallelize", "set_pcheader", "set_pcxxheader",
            "set_plat", "set_pmheader", "set_pmxxheader", "set_policy", "set_prefixdir",
            "set_prefixname", "set_project", "set_rules", "set_runargs", "set_rundir",
            "set_runenv", "set_runtimes", "set_sdkdir", "set_showmenu", "set_sourcedir",
            "set_sourcekinds", "set_strip", "set_suffixname", "set_symbols", "set_targetdir",
            "set_toolchains", "set_toolset", "set_urls", "set_values", "set_version",
            "set_warnings", "set_xmakever", "target", "todisplay", "tonumber",
            "toolchain", "tostring", "try", "type", "unpack",
            "val", "vformat", "vprint", "vprintf", "wprint"
        )

        fun updateApis(newApis: Set<String>) {
            XMAKE_APIS = newApis
        }
    }
}
