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
 * @file        XMakeLuaFileType.kt
 *
 */
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