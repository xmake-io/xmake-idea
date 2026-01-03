package io.xmake.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import io.xmake.utils.SystemUtils

class XMakeMenuGroup : DefaultActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project ?: return super.getChildren(e)
        if (SystemUtils.isXMakeProject(project)) {
            return super.getChildren(e)
        }
        
        // Not xmake project -> return only QuickStart
        val quickStart = ActionManager.getInstance().getAction("XMake.QuickStart")
        return if (quickStart != null) arrayOf(quickStart) else AnAction.EMPTY_ARRAY
    }
}
