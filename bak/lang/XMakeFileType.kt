package org.tboox.xmake.lang

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import org.tboox.xmake.icons.XMakeIcons
import javax.swing.Icon

object XMakeFileType : LanguageFileType(XMakeLanguage) {

    override fun getName(): String = "Rust"

    override fun getIcon(): Icon = XMakeIcons.FILE

    override fun getDefaultExtension(): String = "lua"

    override fun getCharset(file: VirtualFile, content: ByteArray): String = "UTF-8"

    override fun getDescription(): String = "XMake Files"
}

