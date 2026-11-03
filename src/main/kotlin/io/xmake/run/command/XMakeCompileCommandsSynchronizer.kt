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
 *
 * @file        XMakeCompileCommandsSynchronizer.kt
 *
 */
package io.xmake.run.command

import com.intellij.execution.ExecutionTargetListener
import com.intellij.execution.ExecutionTargetManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import io.xmake.build.XMakeBuildTask
import io.xmake.build.XMakeBuildTaskExecution
import io.xmake.project.XMakeSettings
import io.xmake.project.directory.isXMakeLuaVfsEvent
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.project.profile.XMakeBuildProfileManager
import io.xmake.run.target.activeOrSingleXMakeBuildProfile
import io.xmake.utils.info.XMakeInfoManager
import io.xmake.utils.notify
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class XMakeCompileCommandsSynchronizer(
    private val project: Project,
    scope: CoroutineScope,
) : Disposable {

    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private var notifiedNotUpdatedReason: String? = null

    init {
        scope.launch {
            refreshRequests.consumeAsFlow().collect {
                try {
                    refreshCompileCommands()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.warn("Failed to update the compile commands", error)
                }
            }
        }
        val connection = project.messageBus.connect(this)
        connection.subscribe(
            ExecutionTargetManager.TOPIC,
            ExecutionTargetListener { scheduleRefresh() },
        )
        connection.subscribe(
            XMakeInfoManager.TOPIC,
            XMakeInfoManager.Listener { scheduleRefresh() },
        )
        connection.subscribe(
            XMakeBuildProfileManager.TOPIC,
            XMakeBuildProfileManager.Listener { scheduleRefresh() },
        )
        VirtualFileManager.getInstance().addAsyncFileListener({ events ->
            if (events.any { isXMakeLuaVfsEvent(it, includeContentChanges = true) }) {
                scheduleRefresh()
            }
            null
        }, this)
    }

    override fun dispose() {
    }

    fun scheduleRefresh() {
        if (project.isDisposed) return
        refreshRequests.trySend(Unit)
    }

    /** The open-time refresh only fills a missing file; every change has its own trigger. */
    fun scheduleInitialRefresh() {
        if (project.isDisposed) return
        if (!compileCommandsFileExists()) scheduleRefresh()
    }

    private fun compileCommandsFileExists(): Boolean {
        val directory = project.xmakeProjectDirectories.resolveLocalSyncDirectory()
        val generatedPath = compileCommandsGeneratedPath(XMakeSettings.getInstance(project).state.compileCommandsPath)
        return File(directory, generatedPath).isFile
    }

    private suspend fun refreshCompileCommands() {
        val profile = project.activeOrSingleXMakeBuildProfile ?: return
        if (!XMakeSettings.getInstance(project).state.autoUpdateCompileCommands) return
        val toolkit = profile.resolveToolkit(project)?.takeIf { it.isAvailable }
        if (toolkit == null) {
            notifyNotUpdated("The XMake toolkit for profile '${profile.name}' is unavailable")
            return
        }
        if (XMakeInfoManager.getInstance(project).versionRefusal(toolkit) != null) return
        val commands = try {
            project.xmakeCommands(profile)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.warn("Cannot build the XMake commands for profile '${profile.name}'", error)
            notifyNotUpdated("The XMake configuration for profile '${profile.name}' is invalid")
            return
        }
        notifiedNotUpdatedReason = null
        project.xmakeExecutor.submit {
            XMakeBuildTaskExecution.run(
                project,
                XMakeBuildTask(
                    presentableName = "Update compile commands for '${profile.name}'",
                    fetchOnSuccess = commands.compileCommandsGeneratedPath,
                    commands = listOf(commands.createConfigure(), commands.createUpdateCompileCommands()),
                ),
            )
        }.await()
    }

    /** One balloon per stall streak: a successful refresh resets the memory. */
    private fun notifyNotUpdated(reason: String) {
        if (notifiedNotUpdatedReason == reason) return
        notifiedNotUpdatedReason = reason
        Log.warn(reason)
        project.notify(
            "Compile commands were not updated",
            "${StringUtil.escapeXmlEntities(reason)}, so compile_commands.json was not refreshed. " +
                    "Fix the problem, then save xmake.lua or switch the execution target to retry.",
            NotificationType.WARNING,
        )
    }

}

class XMakeCompileCommandsSyncActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<XMakeCompileCommandsSynchronizer>().scheduleInitialRefresh()
    }
}

private val Log = logger<XMakeCompileCommandsSynchronizer>()
