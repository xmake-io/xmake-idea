package io.xmake.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import io.xmake.project.directory.hasXMakeProjectDirectorySource

class XMakeMenuGroup : DefaultActionGroup() {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project
        if (project == null || project.hasXMakeProjectDirectorySource) {
            return super.getChildren(e)
        }

        // Keep both recovery entries until an XMake project directory is configured:
        // select an existing root or create a new project.
        val actionManager = ActionManager.getInstance()
        val configureAction = actionManager.getAction("XMake.EditBuildProfiles")
        val quickStartAction = actionManager.getAction("XMake.QuickStart")
        return listOfNotNull(configureAction, quickStartAction).toTypedArray()
    }
}
