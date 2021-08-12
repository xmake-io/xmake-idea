package io.xmake.project

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import io.xmake.icons.XMakeIcons
import io.xmake.shared.XMakeProblem
import java.awt.Font
import java.awt.event.MouseEvent
import java.awt.event.MouseAdapter
import javax.swing.JScrollPane
import javax.swing.JList
import java.io.File
import javax.swing.ListSelectionModel
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import io.xmake.shared.xmakeConfiguration
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes


class XMakeToolWindowProblemPanel(project: Project) : SimpleToolWindowPanel(false) {

    // the problems
    private var _problems: List<XMakeProblem> = emptyList()
    var problems: List<XMakeProblem>
        get() = _problems
        set(value) {
            check(ApplicationManager.getApplication().isDispatchThread)
            _problems = value
            problemList.setListData(problems.toTypedArray())
        }

    // the toolbar
    val toolbar: ActionToolbar = run {
        val actionManager = ActionManager.getInstance()
        actionManager.createActionToolbar("XMake Toolbar", actionManager.getAction("XMake.Menu") as DefaultActionGroup, false)
    }

    // the problem list
    private val problemList = JBList<XMakeProblem>(emptyList()).apply {
        emptyText.text = "There are no compiling problems to display."
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        cellRenderer = object : ColoredListCellRenderer<XMakeProblem>() {
            override fun customizeCellRenderer(list: JList<out XMakeProblem>, value: XMakeProblem, index: Int, selected: Boolean, hasFocus: Boolean) {

                // get file path
                var file = value.file
                if (file === null) {
                    return
                }

                // init icon
                if (value.kind == "warning") {
                    icon = XMakeIcons.WARNING
                } else if (value.kind == "error") {
                    icon = XMakeIcons.ERROR
                } else {
                    icon = XMakeIcons.WARNING
                }

                // init tips
                toolTipText = value.message ?: ""

                // append text
                append("${file}(${value.line ?: "0"}): ${value.message ?: ""}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
            }
        }
    }

    // the problem pane
    val problemPane = JScrollPane(problemList).apply {
        border = null
    }

    // the content
    val content = panel {
        row {
            problemPane(CCFlags.push, CCFlags.grow)
        }
    }

    init {

        // init toolbar
        setToolbar(toolbar.component)
        toolbar.setTargetComponent(this)

        // init content
        setContent(content)

        // init double click listener
        problemList.addMouseListener(object: MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.getClickCount() == 1 || e.getClickCount() == 2) {

                    // get the clicked problem
                    val index   = problemList.locationToIndex(e.getPoint())
                    if (index < problems.size && problems[index].file !== null) {

                        // get file path
                        val problem     = problems[index]
                        var filename    = problem.file
                        if (File(filename).exists()) {
                            filename = File(filename).getAbsolutePath()
                        } else {
                            filename = File(project.xmakeConfiguration.data.workingDirectory, filename).getAbsolutePath()
                        }

                        // open this file
                        val file = LocalFileSystem.getInstance().findFileByPath(filename)
                        if (file !== null) {

                            // goto file:line
                            val descriptor = OpenFileDescriptor(project, file, problem.line?.toInt() ?: 0, problem.column?.toInt() ?: 0)
                            descriptor.navigate(true)

                            // highlight line
                            val editor = FileEditorManager.getInstance(project).selectedTextEditor
                            if (editor !== null) {

                                if (e.getClickCount() == 2 && editor.markupModel.allHighlighters.size > 0) {
                                    editor.markupModel.removeAllHighlighters()
                                    return
                                }

                                // get line offset
                                var line = problem.line?.toInt() ?: 0
                                if (line > 0) line -= 1

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

    override fun getData(dataId: String): Any? {
        return super.getData(dataId)
    }

    companion object {
        private val Log = Logger.getInstance(XMakeToolWindowProblemPanel::class.java.getName())
    }
}
