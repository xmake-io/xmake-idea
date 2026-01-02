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
 * @file        XMakeLuaFile.kt
 *
 */
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
