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
 * @file        XMakeToolWindowProblemPanel.kt
 *
 */
package io.xmake.project.console

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import io.xmake.icons.XMakeIcons
import io.xmake.shared.XMakeProblem
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.JList
import javax.swing.ListSelectionModel

class XMakeToolWindowProblemPanel(project: Project) : SimpleToolWindowPanel(false) {

    // the problems
    private var _problems: List<XMakeProblem> = emptyList()
    var problems: List<XMakeProblem>
        get() = _problems
        set(value) {
//            check(ApplicationManager.getApplication().isDispatchThread)
            _problems = value
            problemList.setListData(problems.toTypedArray())
        }

    // the toolbar
    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar("XMake Toolbar", actionManager.getAction("XMake.ToolBar") as DefaultActionGroup, false)
    }

    // the problem list
    private val problemList = JBList<XMakeProblem>(emptyList()).apply {
        emptyText.text = "There are no compiling problems to display."
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : ColoredListCellRenderer<XMakeProblem>() {
            override fun customizeCellRenderer(list: JList<out XMakeProblem>, value: XMakeProblem, index: Int, selected: Boolean, hasFocus: Boolean) {

                // init icon
                icon = if (value.kind == "error") XMakeIcons.ERROR else XMakeIcons.WARNING

                // init tips
                toolTipText = value.message ?: ""

                // append text
                val file = value.file
                if (file !== null) {
                    append("${file}(${value.line ?: "0"}): ${value.message ?: ""}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                } else {
                    append(value.message ?: "", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                }
            }
        }
    }

    // the problem pane
    val problemPane = JBScrollPane(problemList).apply {
        border = null
    }

    // the content
    val content = panel {
        row {
            scrollCell(problemList)
                .align(AlignX.FILL)
        }
    }
    /*
    val content = panel {
        row {
            problemPane(CCFlags.push, CCFlags.grow)
        }
    }
    */

    init {

        // init toolbar
        setToolbar(toolbar.component)
        toolbar.targetComponent = this

        // init content
        setContent(content)

        // init double click listener
        problemList.addMouseListener(object: MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1 || e.clickCount == 2) {

                    // get the clicked problem
                    val index   = problemList.locationToIndex(e.getPoint())
                    if (index in problems.indices) {

                        // get file path
                        val problem     = problems[index]
                        val filename = resolveProblemPath(problem) ?: return

                        // open this file
                        val file = LocalFileSystem.getInstance().findFileByPath(filename.toString())
                        if (file !== null) {

                            // compiler line and column numbers are 1-based; the editor APIs are 0-based
                            val line = (problem.line?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
                            val column = (problem.column?.toIntOrNull() ?: 0).coerceAtLeast(0)

                            // goto file:line
                            val descriptor = OpenFileDescriptor(project, file, line, column)
                            descriptor.navigate(true)

                            // highlight line
                            val editor = FileEditorManager.getInstance(project).selectedTextEditor
                            if (editor !== null) {

                                if (e.clickCount == 2 && editor.markupModel.allHighlighters.size > 0) {
                                    editor.markupModel.removeAllHighlighters()
                                    return
                                }

                                // init box color
                                var boxcolor = JBColor.GRAY
                                if (problem.kind == "warning") {
                                    boxcolor = JBColor.YELLOW
                                } else if (problem.kind == "error") {
                                    boxcolor = JBColor.RED
                                }

                                // draw box
                                editor.markupModel.addLineHighlighter(line, -1, TextAttributes(null, null, boxcolor, EffectType.BOXED, Font.PLAIN))
                            }
                        }
                    }
                }
            }
        })
    }
}

internal fun resolveProblemPath(problem: XMakeProblem): Path? = problem.resolvedFilePath
