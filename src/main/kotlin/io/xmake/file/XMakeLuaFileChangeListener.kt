package io.xmake.file

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter

class XMakeLuaFileChangeListener : PsiTreeAnyChangeAbstractAdapter() {
    private val fileDocumentManager = FileDocumentManager.getInstance()

    override fun onChange(file: PsiFile?) {
        file?.let {
            if (XMakeLuaFileType.isFileOfType(file.virtualFile)) {
                println("${file.name} on change")
                fileDocumentManager.saveDocument(it.fileDocument)
            }
        }
    }
}