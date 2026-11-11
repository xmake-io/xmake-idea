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

import com.intellij.execution.ExecutionTargetListener
import com.intellij.execution.ExecutionTargetManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import com.jetbrains.cidr.cpp.toolchains.CPPToolchainsListener
import com.jetbrains.cidr.system.HostData
import io.xmake.project.XMakeSettings
import io.xmake.run.command.COMPILE_COMMANDS_FILE_NAME
import io.xmake.run.command.XMakeCompileCommandsContext
import io.xmake.run.command.XMakeCompileCommandsToolchainRequirement
import io.xmake.run.command.compileCommandsContext
import io.xmake.run.command.compileCommandsFile
import io.xmake.run.command.sshConfig
import io.xmake.run.command.toolchainRequirement
import io.xmake.run.command.wslDistributionName
import io.xmake.utils.notify
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Log = logger<XMakeCompDBToolchainBindingService>()

@Service(Service.Level.PROJECT)
class XMakeCompDBToolchainBindingService(
    private val project: Project,
    private val scope: CoroutineScope,
) : Disposable {

    @Volatile
    private var lastEnvironmentKey: String? = null

    private val environmentLock = Any()

    @Volatile
    private var environmentGeneration = 0L

    @Volatile
    private var writingBinding = false

    private var notifiedMissingKind: String? = null

    private val choices = XMakeCompDBToolchainChoices.getInstance(project)
    private val derivedToolchains = XMakeDerivedToolchains.getInstance()

    init {
        VirtualFileManager.getInstance().addAsyncFileListener({ events ->
            if (events.any { it.isCompileCommandsFileChange(project) }) {
                object : AsyncFileListener.ChangeApplier {
                    override fun afterVfsChange() {
                        if (project.isDisposed) return
                        autoLinkCompilationDatabase(project)
                        // Nothing re-imports the changed file; bind before marking so the
                        // import uses the new toolchain.
                        val file = compileCommandsFile(project)
                        if (file == null || !Files.isRegularFile(file)) return
                        scope.launch {
                            bindSafely()
                            if (!project.isDisposed && isCompilationDatabaseInUse()) {
                                CompDBUtil.markAllCompDBProjectsDirty(project)
                            }
                        }
                    }
                }
            } else {
                null
            }
        }, this)
        project.messageBus.connect(this).subscribe(
            ExecutionTargetManager.TOPIC,
            ExecutionTargetListener {
                synchronized(environmentLock) {
                    environmentGeneration++
                    lastEnvironmentKey = null
                }
            },
        )
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            CPPToolchainsListener.TOPIC,
            object : CPPToolchainsListener {
                override fun toolchainCMakeEnvironmentChanged(toolchains: Set<CPPToolchains.Toolchain>) {
                    if (project.isDisposed) return
                    val current = CompDBLocalSettings.getInstance(project).toolchainName ?: return
                    val registry = CPPToolchains.getInstance().toolchains
                    if (toolchains.none { it.name == current && registry.none { c -> c.name == it.name } }) {
                        return
                    }
                    scope.launch {
                        val settings = CompDBLocalSettings.getInstance(project)
                        val before = settings.toolchainName
                        if (settings.toolchainName == current) {
                            bindToolchain(settings, null)
                        }
                        bindSafely()
                        if (!project.isDisposed &&
                            settings.toolchainName != before &&
                            isCompilationDatabaseInUse()
                        ) {
                            CompDBUtil.markAllCompDBProjectsDirty(project)
                        }
                    }
                }
            },
        )
        project.messageBus.connect(this).subscribe(
            CompDBSettingsListener.TOPIC,
            object : CompDBSettingsListener {
                override fun onToolchainChanged(oldToolchainName: String?, newToolchainName: String?) {
                    if (writingBinding) return
                    val environmentKey = lastEnvironmentKey
                    if (environmentKey == null) {
                        choices.storePending(newToolchainName)
                    } else {
                        choices.store(environmentKey, newToolchainName)
                    }
                }
            },
        )
    }

    internal suspend fun bindBeforeLink() {
        if (project.isDisposed) return
        val settings = CompDBLocalSettings.getInstance(project)
        val before = settings.toolchainName
        bindSafely()
        if (!project.isDisposed &&
            settings.toolchainName != before &&
            CompDBUtil.linkedProjectSettings(project).isNotEmpty()
        ) {
            CompDBUtil.markAllCompDBProjectsDirty(project)
        }
    }

    private suspend fun bindSafely() {
        try {
            bind()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warn("Could not bind the compilation database toolchain", error)
        }
    }

    private suspend fun bind() {
        if (project.isDisposed) return
        val generation = environmentGeneration
        val context = compileCommandsContext(project) ?: return
        val requirement = context.toolchainRequirement() ?: return
        val environmentKey = context.environmentKey() ?: return
        if (environmentGeneration != generation) return
        synchronized(environmentLock) {
            if (environmentGeneration == generation) {
                lastEnvironmentKey = environmentKey
            }
        }
        if (!isCompilationDatabaseInUse()) return
        val settings = CompDBLocalSettings.getInstance(project)
        val current = settings.toolchainName
        val pending = choices.takePending()
        if (pending != null) {
            choices.store(environmentKey, (pending as? ManualChoice.Named)?.name)
        }
        val manual = pending ?: choices.stored(environmentKey)
            ?: current?.takeIf { !choices.isBindingAdopted() }?.let { name ->
                when {
                    CPPToolchains.getInstance().toolchains.none { it.name == name } -> {
                        bindToolchain(settings, null)
                        null
                    }
                    derivedToolchains.contains(name) -> null
                    else -> {
                        choices.store(environmentKey, name)
                        ManualChoice.Named(name)
                    }
                }
            }
        choices.markBindingAdopted()
        if (manual == null && current != null && settings.toolchainName == current &&
            CPPToolchains.getInstance().toolchains.none { it.name == current }
        ) {
            bindToolchain(settings, null)
        }
        when (manual) {
            null -> {}
            ManualChoice.Default -> {
                if (current != null) bindToolchain(settings, null)
                return
            }
            is ManualChoice.Named -> {
                val name = manual.name
                if (CPPToolchains.getInstance().toolchains.any { it.name == name }) {
                    if (current != name) bindToolchain(settings, name)
                    return
                }
                choices.clear(environmentKey)
                if (current == name) bindToolchain(settings, null)
            }
        }
        val existing = matchingExistingToolchain(requirement, context)
        if (existing != null) {
            if (current == existing.name) return
            bindToolchain(settings, existing.name)
            return
        }
        val derived = deriveToolchainIfNeeded(requirement, context) ?: run {
            notifyMissingToolchain(requirement)
            return
        }
        bindToolchain(settings, derived.name)
    }

    private fun isCompilationDatabaseInUse(): Boolean =
        XMakeSettings.getInstance(project).state.autoLinkCompilationDatabase ||
                CompDBUtil.linkedProjectSettings(project).isNotEmpty()

    private fun matchingExistingToolchain(
        requirement: XMakeCompileCommandsToolchainRequirement,
        context: XMakeCompileCommandsContext,
    ): CPPToolchains.Toolchain? {
        val candidates = CPPToolchains.getInstance().toolchains.filter {
            it.toolSetKind in requirement.preferredToolSetKinds()
        }
        return when (requirement) {
            XMakeCompileCommandsToolchainRequirement.WSL -> {
                val distribution = context.wslDistributionName
                candidates.firstOrNull { it.toolSetPath.equals(distribution, ignoreCase = true) }
            }
            XMakeCompileCommandsToolchainRequirement.SSH -> {
                val configId = context.sshConfig?.id
                candidates.firstOrNull { configId != null && it.hostData?.sshConfig?.id == configId }
            }
            else -> requirement.preferredToolSetKinds().firstNotNullOfOrNull { kind ->
                candidates.firstOrNull { it.toolSetKind == kind }
            }
        }
    }

    /** The settings change detector holds the pre-change name, so the name is written twice. */
    private suspend fun bindToolchain(settings: CompDBLocalSettings, name: String?) {
        withContext(Dispatchers.EDT) {
            writingBinding = true
            try {
                settings.toolchainName = name
                settings.toolchainName = name
            } finally {
                writingBinding = false
            }
        }
        if (name == null) {
            Log.info("Cleared the compilation database toolchain binding")
        } else {
            Log.info("Bound the compilation database toolchain to '$name'")
        }
    }

    @Suppress("OPT_IN_USAGE")
    private suspend fun deriveToolchainIfNeeded(
        requirement: XMakeCompileCommandsToolchainRequirement,
        context: XMakeCompileCommandsContext,
    ): CPPToolchains.Toolchain? = edtWriteAction {
        val name = context.derivedToolchainName(requirement) ?: return@edtWriteAction null
        if (wasDerivedToolchainDeleted(name)) {
            clearDanglingBinding(name)
            return@edtWriteAction null
        }
        val existing = CPPToolchains.getInstance().toolchains.firstOrNull { it.name == name }
        if (existing != null) {
            if (!derivedToolchains.contains(name)) return@edtWriteAction null
            if (existing.toolSetKind !in requirement.preferredToolSetKinds()) return@edtWriteAction null
            repairPersistedRemoteIdentity(existing, requirement, context)
            return@edtWriteAction existing
        }
        val toolchain = when (requirement) {
            XMakeCompileCommandsToolchainRequirement.WSL -> {
                val distribution = context.wslDistributionName ?: return@edtWriteAction null
                CPPToolchains.Toolchain(OS.CURRENT).apply {
                    setName(name)
                    toolSetKind = CPPToolSet.Kind.WSL
                    toolSetPath = distribution
                }
            }
            XMakeCompileCommandsToolchainRequirement.SSH -> {
                val config = context.sshConfig ?: return@edtWriteAction null
                CPPToolchains.Toolchain(OS.CURRENT).apply {
                    setName(name)
                    toolSetKind = CPPToolSet.Kind.SSH
                    hostData = HostData(config.id, config)
                }
            }
            else -> return@edtWriteAction null
        }
        // setToolSetKind silently keeps the default kind when the kind is not
        // supported on this OS; a toolchain of the wrong kind must not be added.
        if (toolchain.toolSetKind !in requirement.preferredToolSetKinds()) return@edtWriteAction null
        CPPToolchains.getInstance().apply {
            beginUpdate()
            try {
                addToolchain(toolchain)
            } finally {
                endUpdate()
            }
        }
        derivedToolchains.add(name)
        toolchain
    }

    private fun repairPersistedRemoteIdentity(
        toolchain: CPPToolchains.Toolchain,
        requirement: XMakeCompileCommandsToolchainRequirement,
        context: XMakeCompileCommandsContext,
    ) {
        when (requirement) {
            XMakeCompileCommandsToolchainRequirement.WSL -> {
                val distribution = context.wslDistributionName ?: return
                if (!toolchain.toolSetPath.equals(distribution, ignoreCase = true)) {
                    toolchain.toolSetPath = distribution
                }
            }
            XMakeCompileCommandsToolchainRequirement.SSH -> {
                val config = context.sshConfig ?: return
                if (toolchain.hostData?.sshConfig?.id != config.id) {
                    toolchain.hostData = HostData(config.id, config)
                }
            }
            else -> {}
        }
    }

    private fun wasDerivedToolchainDeleted(name: String): Boolean =
        derivedToolchains.contains(name) &&
                CPPToolchains.getInstance().toolchains.none { it.name == name }

    private fun clearDanglingBinding(name: String) {
        val settings = CompDBLocalSettings.getInstance(project)
        if (settings.toolchainName != name) return
        writingBinding = true
        try {
            settings.toolchainName = null
            settings.toolchainName = null
        } finally {
            writingBinding = false
        }
        Log.info("Cleared the compilation database binding to the deleted toolchain '$name'")
    }

    private fun notifyMissingToolchain(requirement: XMakeCompileCommandsToolchainRequirement) {
        val kindName = requirement.preferredToolSetKinds().first().displayName
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

private fun XMakeCompileCommandsToolchainRequirement.preferredToolSetKinds(): List<CPPToolSet.Kind> = when (this) {
    XMakeCompileCommandsToolchainRequirement.SYSTEM -> listOf(CPPToolSet.Kind.SYSTEM_UNIX_TOOLSET)
    XMakeCompileCommandsToolchainRequirement.WSL -> listOf(CPPToolSet.Kind.WSL)
    XMakeCompileCommandsToolchainRequirement.SSH -> listOf(CPPToolSet.Kind.SSH)
    XMakeCompileCommandsToolchainRequirement.MSVC ->
        listOf(CPPToolSet.Kind.MSVC, CPPToolSet.Kind.SYSTEM_WINDOWS_TOOLSET)
    XMakeCompileCommandsToolchainRequirement.MINGW ->
        listOf(CPPToolSet.Kind.MINGW, CPPToolSet.Kind.SYSTEM_WINDOWS_TOOLSET)
}

private fun XMakeCompileCommandsContext.derivedToolchainName(
    requirement: XMakeCompileCommandsToolchainRequirement,
): String? = when (requirement) {
    XMakeCompileCommandsToolchainRequirement.WSL ->
        wslDistributionName?.let { "${DERIVED_PREFIX}WSL $it" }
    XMakeCompileCommandsToolchainRequirement.SSH -> sshConfig?.let { config ->
        "${DERIVED_PREFIX}${config.presentableShortName} (${config.id.take(8)})"
    }
    else -> null
}

private fun XMakeCompileCommandsContext.environmentKey(): String? {
    val requirement = toolchainRequirement() ?: return null
    return when (requirement) {
        XMakeCompileCommandsToolchainRequirement.WSL -> wslDistributionName?.lowercase()?.let { "WSL:$it" }
        XMakeCompileCommandsToolchainRequirement.SSH -> sshConfig?.id?.let { "SSH:$it" }
        XMakeCompileCommandsToolchainRequirement.SYSTEM,
        XMakeCompileCommandsToolchainRequirement.MSVC,
        XMakeCompileCommandsToolchainRequirement.MINGW,
        -> requirement.name
    }
}

private const val DERIVED_PREFIX = "XMake - "

private fun VFileEvent.isCompileCommandsFileChange(project: Project): Boolean {
    if (!path.endsWith(COMPILE_COMMANDS_FILE_NAME, ignoreCase = true)) return false
    val file = compileCommandsFile(project) ?: return false
    return FileUtil.pathsEqual(path, file.toString())
}
