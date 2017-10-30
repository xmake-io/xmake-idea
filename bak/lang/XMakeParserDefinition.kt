package org.tboox.xmake.lang

import com.intellij.lang.*
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.tboox.xmake.lang.psi.XMakeFile

class XMakeParserDefinition : ParserDefinition {

    override fun createFile(viewProvider: FileViewProvider): PsiFile? = XMakeFile(viewProvider)

    override fun spaceExistanceTypeBetweenTokens(left: ASTNode, right: ASTNode): ParserDefinition.SpaceRequirements {
        return ParserDefinition.SpaceRequirements.MAY
    }

    override fun getFileNodeType(): IFileElementType = AnnotationTarget.FILE

    override fun getStringLiteralElements(): TokenSet = STRINGS

    override fun getWhitespaceTokens(): TokenSet = WHITE_SPACES

    override fun getCommentTokens() = COMMENTS

    override fun createElement(node: ASTNode?): PsiElement = PsiElement()

    override fun createLexer(project: Project?): Lexer = null

    override fun createParser(project: Project?): PsiParser = null

    companion object {
        val WHITE_SPACES = TokenSet.create(TokenType.WHITE_SPACE)
        val COMMENTS = TokenSet.create(IElementType("COMMENTS", Language.ANY))
        val STRINGS = TokenSet.create(IElementType("STRINGS", Language.ANY))
    }
}
