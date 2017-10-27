package org.tboox.xmake.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.diagnostic.Logger;

public class BuildAction extends AnAction {

    // get log
    private static final Logger Log = Logger.getInstance(BuildAction.class.getName());

    @Override
    public void actionPerformed(AnActionEvent e) {

        Project project = e.getProject();
        String s = Messages.showInputDialog(project, "What's your name?", "Hello", Messages.getQuestionIcon());
        Messages.showMessageDialog(project, "Hello " + s + "!", "Welcome", Messages.getInformationIcon());
    }
}
