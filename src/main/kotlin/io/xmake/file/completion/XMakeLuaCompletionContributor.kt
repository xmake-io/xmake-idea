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
 * @file        XMakeLuaCompletionContributor.kt
 *
 */
package io.xmake.file.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import io.xmake.file.highlight.XMakeLuaLexer
import io.xmake.file.highlight.XMakeLuaTokenTypes

class XMakeLuaCompletionContributor : CompletionContributor() {

    private var cachedApiElements: List<LookupElementBuilder> = emptyList()
    private var cachedKeywordElements: List<LookupElementBuilder> = emptyList()
    private var lastApiSet: Set<String> = emptySet()

    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement()
                .andNot(PlatformPatterns.psiElement().withElementType(XMakeLuaTokenTypes.COMMENT))
                .andNot(PlatformPatterns.psiElement().withElementType(XMakeLuaTokenTypes.STRING)),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val currentApis = XMakeLuaLexer.XMAKE_APIS

                    synchronized(this) {
                        if (currentApis !== lastApiSet) {
                            lastApiSet = currentApis
                            cachedApiElements = currentApis.map {
                                LookupElementBuilder.create(it).withIcon(AllIcons.Nodes.Method)
                            }
                        }

                        if (cachedKeywordElements.isEmpty()) {
                            cachedKeywordElements = XMakeLuaLexer.LUA_KEYWORDS.map {
                                LookupElementBuilder.create(it).withBoldness(true)
                            }
                        }
                    }

                    result.addAllElements(cachedApiElements)
                    result.addAllElements(cachedKeywordElements)
                }
            }
        )
    }
}
