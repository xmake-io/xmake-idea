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
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.run.target.activeOrSingleXMakeBuildProfile
import io.xmake.utils.path.WorkingDirectoryResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import java.nio.file.Path

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
    }

    override fun dispose() {
    }

    fun scheduleRefresh() {
        if (project.isDisposed) return
        refreshRequests.trySend(Unit)
    }

    private suspend fun refreshCompileCommands() {
        val profile = project.activeOrSingleXMakeBuildProfile ?: return
        if (profile.resolveToolkit(project)?.isAvailable != true) return
        val commands = project.xmakeCommands(profile)
        val generated = commands.compileCommandsGeneratedPath
        val configured = project.xmakeProjectDirectories.state.localDirectory.ifBlank { project.basePath.orEmpty() }
        val projectDirectory = try {
            WorkingDirectoryResolver.resolve(project, configured)
        } catch (error: RuntimeException) {
            Log.warn("Failed to resolve the directory $configured", error)
            return
        }
        if (projectDirectory.isEmpty() || !Path.of(projectDirectory, generated).toFile().isFile) return
        project.xmakeExecutor.submit {
            project.xmakeExecutor.captureStandardOutput(commands.createConfigure())
            project.xmakeExecutor.captureStandardOutput(commands.createUpdateCompileCommands())
        }.await()
    }
}

/** Runs the first refresh pass shortly after the project opens. */
class XMakeCompileCommandsSyncActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<XMakeCompileCommandsSynchronizer>().scheduleRefresh()
    }
}

private val Log = logger<XMakeCompileCommandsSynchronizer>()
