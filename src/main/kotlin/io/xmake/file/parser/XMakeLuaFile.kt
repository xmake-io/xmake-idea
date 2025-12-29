package io.xmake.file.parser

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import io.xmake.file.XMakeLuaFileType
import io.xmake.file.XMakeLuaLanguage

class XMakeLuaFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, XMakeLuaLanguage.INSTANCE) {
    override fun getFileType(): FileType = XMakeLuaFileType.INSTANCE
    override fun toString(): String = "XMake Lua File"
}
