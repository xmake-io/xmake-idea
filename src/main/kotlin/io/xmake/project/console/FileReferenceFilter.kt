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
 */
package io.xmake.project.console

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

class FileReferenceFilter(private val project: Project) : Filter {

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = PATTERNS.firstNotNullOfOrNull { it.find(line) } ?: return null
        val file = resolve(match.group("path") ?: return null) ?: return null
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file)
            ?.takeUnless { it.isDirectory } ?: return null
        val lineNumber = zeroBased(match.group("line")) ?: return null
        val columnNumber = zeroBased(match.group("column")) ?: 0
        // The result spans the whole document, so the match is rebased onto the line's start.
        val lineStart = entireLength - line.length
        return Filter.Result(
            lineStart + match.range.first,
            lineStart + match.range.last + 1,
            OpenFileHyperlinkInfo(project, virtualFile, lineNumber, columnNumber),
        )
    }

    private fun resolve(path: String): File? {
        val file = File(path)
        if (file.isAbsolute) return file
        return project.basePath?.let { File(it, path) }
    }

    private fun zeroBased(number: String?): Int? =
        number?.toIntOrNull()?.minus(1)?.coerceAtLeast(0)

    private fun MatchResult.group(name: String): String? = groups[name]?.value

    private companion object {
        val GCC_STYLE = Regex("""(?<path>(?:[A-Za-z]:)?[^\s:()]+):(?<line>\d+)(?::(?<column>\d+))?""")
        val MSVC_FAMILY = Regex("""(?<path>(?:[A-Za-z]:)?[^\s:()]+)\((?<line>\d+)(?:,(?<column>\d+))?\)""")
        val PATTERNS = listOf(GCC_STYLE, MSVC_FAMILY)
    }
}
