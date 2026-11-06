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
 */

package io.xmake.debug.clion.compdb

import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.system.OS
import com.jetbrains.cidr.cpp.compdb.CompDBUtil
import com.jetbrains.cidr.cpp.compdb.settings.CompDBLocalSettings
import com.jetbrains.cidr.cpp.compdb.settings.CompDBSettingsListener
import com.jetbrains.cidr.cpp.toolchains.CPPToolSet
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import com.jetbrains.cidr.system.HostData
import io.xmake.run.command.COMPILE_COMMANDS_FILE_NAME
import io.xmake.run.command.XMakeCompileCommandsArtifact
import io.xmake.run.command.XMakeCompileCommandsDomain
import io.xmake.run.command.compileCommandsArtifact
import io.xmake.run.command.compileCommandsArtifactFile
import io.xmake.run.command.domain
import io.xmake.utils.notify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Aligns the Compilation Database project's toolchain with the artifact's domain, so
 *  implicit headers resolve through the matching environment. */
private val Log = logger<XMakeCompDBToolchainAlignmentService>()

@Service(Service.Level.PROJECT)
class XMakeCompDBToolchainAlignmentService(
    private val project: Project,
    private val scope: CoroutineScope,
) : Disposable {

    // A manual pick rules while the build keeps the domain it was made in.
    @Volatile
    private var manualChoice: String? = null

    @Volatile
    private var manualChoiceDomain: XMakeCompileCommandsDomain? = null

    @Volatile
    private var lastDomain: XMakeCompileCommandsDomain? = null

    /** True while this service writes the toolchain name itself. */
    @Volatile
    private var aligning = false

    private var notifiedMissingKind: String? = null

    init {
        VirtualFileManager.getInstance().addAsyncFileListener({ events ->
            if (events.any { it.isArtifactChange(project) }) {
                object : AsyncFileListener.ChangeApplier {
                    override fun afterVfsChange() {
                        linkCompilationDatabaseIfNeeded(project)
                        requestAlignment()
                    }
                }
            } else {
                null
            }
        }, this)
        project.messageBus.connect(this).subscribe(
            CompDBSettingsListener.TOPIC,
            object : CompDBSettingsListener {
                override fun onToolchainChanged(oldToolchainName: String?, newToolchainName: String?) {
                    if (!aligning) {
                        manualChoice = newToolchainName
                        manualChoiceDomain = lastDomain
                    }
                }
            },
        )
    }

    fun requestAlignment() {
        if (project.isDisposed) return
        scope.launch { align() }
    }

    private suspend fun align() {
        if (project.isDisposed) return
        val artifact = compileCommandsArtifact(project) ?: return
        val domain = artifact.domain() ?: return
        lastDomain = domain
        val candidates = CPPToolchains.getInstance().toolchains.filter { it.toolSetKind in domain.kinds() }
        val distribution = artifact.wslDistributionName?.takeIf { domain == XMakeCompileCommandsDomain.WSL }
        val target = distribution?.let { d -> candidates.firstOrNull { it.toolSetPath.equals(d, ignoreCase = true) } }
            ?: candidates.firstOrNull()
            ?: deriveToolchainIfNeeded(domain, artifact)
            ?: run {
                notifyMissingToolchain(domain)
                return
            }
        val settings = CompDBLocalSettings.getInstance(project)
        val current = settings.toolchainName
        if (current == target.name) {
            CompDBUtil.markAllCompDBProjectsDirty(project)
            return
        }
        if (preservesManualChoice(domain, current)) return
        bindToolchain(settings, target.name)
    }

    /** The settings change detector holds the pre-change name, so the bound name is
     *  written twice to settle it. */
    private suspend fun bindToolchain(settings: CompDBLocalSettings, name: String) {
        withContext(Dispatchers.EDT) {
            aligning = true
            try {
                settings.toolchainName = name
                settings.toolchainName = name
            } finally {
                aligning = false
            }
        }
        CompDBUtil.markAllCompDBProjectsDirty(project)
        Log.info("Aligned the compilation database toolchain to '$name'")
    }

    private suspend fun deriveToolchainIfNeeded(
        domain: XMakeCompileCommandsDomain,
        artifact: XMakeCompileCommandsArtifact,
    ): CPPToolchains.Toolchain? = edtWriteAction {
        val displayName = artifact.hostDisplayName ?: return@edtWriteAction null
        val name = "$DERIVED_PREFIX$displayName"
        CPPToolchains.getInstance().toolchains.firstOrNull { it.name == name }
            ?.let { return@edtWriteAction if (it.toolSetKind in domain.kinds()) it else null }
        val toolchain = when (domain) {
            XMakeCompileCommandsDomain.WSL -> {
                val distribution = artifact.wslDistributionName ?: return@edtWriteAction null
                CPPToolchains.Toolchain(OS.CURRENT).apply {
                    setName(name)
                    setToolSetKind(CPPToolSet.Kind.WSL)
                    toolSetPath = distribution
                }
            }
            XMakeCompileCommandsDomain.SSH -> {
                val config = artifact.sshConfig ?: return@edtWriteAction null
                CPPToolchains.Toolchain(OS.CURRENT).apply {
                    setName(name)
                    setToolSetKind(CPPToolSet.Kind.SSH)
                    hostData = HostData(config.id, config)
                }
            }
            else -> return@edtWriteAction null
        }
        CPPToolchains.getInstance().apply {
            beginUpdate()
            try {
                addToolchain(toolchain)
            } finally {
                endUpdate()
            }
        }
        toolchain
    }

    private fun preservesManualChoice(
        domain: XMakeCompileCommandsDomain,
        current: String?,
    ): Boolean {
        val manual = manualChoice ?: return false
        return manual == current && manualChoiceDomain == domain
    }

    private fun notifyMissingToolchain(domain: XMakeCompileCommandsDomain) {
        val kindName = domain.kinds().first().displayName
        if (notifiedMissingKind == kindName) return
        notifiedMissingKind = kindName
        project.notify(
            "No CLion $kindName toolchain found",
            "The generated compile commands need a $kindName toolchain for correct code " +
                    "insight. Add one under Settings > Build, Execution, Deployment > Toolchains.",
            NotificationType.WARNING,
        )
    }

    override fun dispose() {
    }
}

private fun XMakeCompileCommandsDomain.kinds(): List<CPPToolSet.Kind> = when (this) {
    XMakeCompileCommandsDomain.SYSTEM -> listOf(CPPToolSet.Kind.SYSTEM_UNIX_TOOLSET)
    XMakeCompileCommandsDomain.WSL -> listOf(CPPToolSet.Kind.WSL)
    XMakeCompileCommandsDomain.SSH -> listOf(CPPToolSet.Kind.SSH)
    XMakeCompileCommandsDomain.MSVC -> listOf(CPPToolSet.Kind.MSVC, CPPToolSet.Kind.SYSTEM_WINDOWS_TOOLSET)
    XMakeCompileCommandsDomain.MINGW -> listOf(CPPToolSet.Kind.MINGW, CPPToolSet.Kind.SYSTEM_WINDOWS_TOOLSET)
}

private const val DERIVED_PREFIX = "XMake - "

private fun VFileEvent.isArtifactChange(project: Project): Boolean {
    if (!path.endsWith(COMPILE_COMMANDS_FILE_NAME, ignoreCase = true)) return false
    val artifact = compileCommandsArtifactFile(project) ?: return false
    return FileUtil.pathsEqual(path, artifact.toString())
}
