package io.xmake.utils.info

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitChangedNotifier

class XMakeInfoActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(
                ToolkitChangedNotifier.TOOLKIT_CHANGED_TOPIC,
                object : ToolkitChangedNotifier {
                    override fun toolkitChanged(toolkit: Toolkit?) {
                        XMakeInfoManager.getInstance(project).probeXMakeInfo(toolkit)
                    }
                }
            )
    }
}