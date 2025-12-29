package io.xmake.file.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import io.xmake.file.highlight.XMakeLuaLexer
import io.xmake.file.highlight.XMakeLuaTokenTypes

class XMakeLuaCompletionContributor : CompletionContributor() {
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
                    val apis = XMakeLuaLexer.XMAKE_APIS
                    apis.forEach { api ->
                        result.addElement(LookupElementBuilder.create(api))
                    }
                    val keywords = XMakeLuaLexer.LUA_KEYWORDS
                    keywords.forEach { keyword ->
                        result.addElement(LookupElementBuilder.create(keyword).withBoldness(true))
                    }
                }
            }
        )
    }
}
