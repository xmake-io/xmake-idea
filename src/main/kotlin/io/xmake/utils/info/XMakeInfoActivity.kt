package io.xmake.utils.info

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitChangedNotifier
import io.xmake.project.toolkit.activatedToolkit

import io.xmake.project.toolkit.ToolkitManager

class XMakeInfoActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Initial probe
        val manager = XMakeInfoManager.getInstance(project)
        val toolkit = project.activatedToolkit ?: ToolkitManager.getInstance().getRegisteredToolkits().firstOrNull()
        
        toolkit?.let {
            manager.probeXMakeInfo(it)
            manager.probeXMakeApis(it)
        }

        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(
                ToolkitChangedNotifier.TOOLKIT_CHANGED_TOPIC,
                object : ToolkitChangedNotifier {
                    override fun toolkitChanged(toolkit: Toolkit?) {
                        manager.probeXMakeInfo(toolkit)
                        manager.probeXMakeApis(toolkit)
                    }
                }
            )
    }
}