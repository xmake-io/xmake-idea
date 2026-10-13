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
 * @file        XMakeToolWindowOutputPanel.kt
 *
 */
package io.xmake.project.console

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

class XMakeToolWindowOutputPanel(project: Project) : SimpleToolWindowPanel(false), Disposable {

    // the toolbar
    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar(
            "XMake Toolbar",
            actionManager.getAction("XMake.ToolBar") as DefaultActionGroup,
            false
        )
    }

    // the console view
    val consoleView: ConsoleView = run {
        val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        builder.setViewer(true)
        builder.addFilter(FileReferenceFilter(project))
        builder.console
    }

    init {

        // init toolbar
        setToolbar(toolbar.component)
        toolbar.targetComponent = this

        // init content
        setContent(consoleView.component)

        // register console view to disposer
        Disposer.register(this, consoleView)
    }

    // dispose
    override fun dispose() {
    }
}

/**
 * Links `file:line:col` references in console output, resolving relative paths against the
 * project base. Remote host paths stay plain text here; the Build view links those.
 */
private class FileReferenceFilter(private val project: Project) : Filter {

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val match = PATTERN.find(line) ?: return null
        val file = resolve(match.groupValues[1]) ?: return null
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file) ?: return null
        val lineNumber = match.groupValues[2].toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: return null
        val columnNumber = match.groupValues[3].toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
        return Filter.Result(
            match.range.first,
            match.range.last + 1,
            OpenFileHyperlinkInfo(project, virtualFile, lineNumber, columnNumber),
        )
    }

    private fun resolve(path: String): File? {
        val file = File(path)
        if (file.isAbsolute) return file
        val basePath = project.basePath ?: return null
        return File(basePath, path)
    }

    private companion object {
        val PATTERN = Regex("""((?:[A-Za-z]:)?[^\s:()]*[/\\][^\s:()]*):(\d+)(?::(\d+))?""")
    }
}
