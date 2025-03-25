package io.xmake.file

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.vfs.VirtualFile
import io.xmake.icons.XMakeIcons
import org.jetbrains.annotations.Contract
import javax.swing.Icon

object XMakeLuaFileType : LanguageFileType(XMakeLuaLanguage) {

    const val FILE_NAME: String = "xmake.lua"
    val INSTANCE: XMakeLuaFileType = XMakeLuaFileType

    override fun getName(): String {
        return FILE_NAME
    }

    override fun getDescription(): String {
        return "XMake Lua file"
    }

    override fun getDefaultExtension(): String {
        return "lua"
    }

    override fun getIcon(): Icon {
        return XMakeIcons.FILE
    }

    override fun getDisplayName(): String {
        return "XMake Lua"
    }


    private fun findLanguage(): Language {
        return Language.findLanguageByID("xmake.lua") ?: PlainTextLanguage.INSTANCE
    }

    @Contract("null->false")
    fun isFileOfType(file: VirtualFile?): Boolean {
        return file != null && FileTypeManager.getInstance().isFileOfType(file, INSTANCE)
    }

}