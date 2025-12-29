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
