package org.tboox.xmake.lang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.tboox.xmake.lang.XMakeFileType
import org.tboox.xmake.lang.XMakeLanguage

class XMakeFile(fileViewProvider: FileViewProvider) : PsiFileBase(fileViewProvider, XMakeLanguage) {

    override fun getFileType(): FileType = XMakeFileType
}
