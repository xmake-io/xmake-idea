package org.tboox.xmake.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.diagnostic.Logger

class QuickStartAction : AnAction() {

    @Override
    override fun actionPerformed(e: AnActionEvent) {

        val project = e.project
        val s = Messages.showInputDialog(project, "What's your name?", "Hello", Messages.getQuestionIcon())
        Messages.showMessageDialog(project, "Hello $s!", "Welcome", Messages.getInformationIcon())
    }

    companion object {

        // get log
        private val Log = Logger.getInstance(BuildAction::class.java.getName())
    }
}
