package io.xmake.utils.extension

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import io.xmake.project.directory.ui.DirectoryBrowser
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.utils.execute.SyncDirection
import kotlinx.coroutines.CoroutineScope
import java.awt.event.ActionListener

interface ToolkitHostExtension {
    val KEY: String

    fun getHostType(): String

    fun getToolkitHosts(project: Project? = null): List<ToolkitHost>

    fun filterRegistered(): (Toolkit) -> Boolean

    fun createToolkit(host: ToolkitHost, path: String, version: String): Toolkit

    fun syncProject(
        scope: CoroutineScope,
        project: Project,
        host: ToolkitHost,
        direction: SyncDirection,
        remoteDirectory: String,
    )

    fun getTargetId(target: Any? = null): String

    suspend fun ToolkitHost.loadTargetX(project: Project? = null)

    fun DirectoryBrowser.createBrowseListener(host: ToolkitHost): ActionListener

    fun GeneralCommandLine.createProcess(host: ToolkitHost): Process
}