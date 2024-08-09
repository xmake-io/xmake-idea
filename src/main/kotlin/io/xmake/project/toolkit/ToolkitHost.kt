package io.xmake.project.toolkit

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ssh.config.unified.SshConfigManager
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import io.xmake.project.toolkit.ToolkitHostType.*
import kotlinx.coroutines.coroutineScope

@Tag("toolkitHost")
data class ToolkitHost(
    @Attribute
    val type: ToolkitHostType = LOCAL,
) {

    constructor(type: ToolkitHostType, target: Any? = null) : this(type) {
        this.target = target
        this.id = when (type) {
            LOCAL -> SystemInfo.getOsName()
            WSL -> (target as WSLDistribution).id
            SSH -> (target as SshConfig).id
        }
    }

    @Transient
    var target: Any? = null
        private set

    @Attribute
    private var id: String? = null

    suspend fun loadTarget(project: Project? = null) {
        when (type) {
            LOCAL -> {}
            WSL -> loadWslTarget()
            SSH -> loadSshTarget(project)
        }
    }

    private suspend fun loadWslTarget() = coroutineScope {
        target = WslDistributionManager.getInstance().installedDistributions.find { it.id == id }!!
    }

    private suspend fun loadSshTarget(project: Project? = null) = coroutineScope {
        target = SshConfigManager.getInstance(project).findConfigById(id!!)!!
    }

    override fun toString(): String {
        return "ToolkitHost(type=$type, id=$id)"
    }
}