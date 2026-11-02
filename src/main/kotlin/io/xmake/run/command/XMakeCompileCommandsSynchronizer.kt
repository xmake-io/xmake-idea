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
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import io.xmake.build.XMakeBuildTask
import io.xmake.build.XMakeBuildTaskExecution
import io.xmake.project.XMakeSettings
import io.xmake.project.directory.isXMakeLuaVfsEvent
import io.xmake.project.profile.XMakeBuildProfile
import io.xmake.run.target.activeOrSingleXMakeBuildProfile
import io.xmake.utils.info.XMakeInfoManager
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
        project.messageBus.connect(this).subscribe(
            ExecutionTargetManager.TOPIC,
            ExecutionTargetListener { scheduleRefresh() },
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

    private suspend fun refreshCompileCommands() {
        val profile = project.activeOrSingleXMakeBuildProfile ?: return
        if (!XMakeSettings.getInstance(project).state.autoUpdateCompileCommands) return
        val toolkit = profile.resolveToolkit(project)?.takeIf { it.isAvailable } ?: return
        if (XMakeInfoManager.getInstance(project).versionRefusal(toolkit) != null) return
        val commands = project.xmakeCommands(profile)
        project.xmakeExecutor.submit {
            XMakeBuildTaskExecution.run(
                project,
                XMakeBuildTask(
                    presentableName = "Update compile commands for '${profile.name}'",
                    commands = listOf(commands.createConfigure(), commands.createUpdateCompileCommands()),
                ),
            )
        }.await()
    }

}

class XMakeCompileCommandsSyncActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<XMakeCompileCommandsSynchronizer>().scheduleRefresh()
    }
}

private val Log = logger<XMakeCompileCommandsSynchronizer>()
