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
package io.xmake.run.target

import com.intellij.execution.DefaultExecutionTarget
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetListener
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.task.ProjectTaskManager
import com.intellij.util.messages.MessageBusConnection
import io.xmake.build.XMakeBuildTask
import io.xmake.project.profile.XMakeBuildProfileManager
import io.xmake.project.profile.xmakeBuildProfiles
import io.xmake.project.directory.XMakeProjectDirectoryManager
import io.xmake.run.XMakeProfileRunConfiguration
import io.xmake.run.command.XMakeCommandFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Keeps the run toolbar target and each XMake run configuration's profile reference in sync. */
@Service(Service.Level.PROJECT)
class XMakeExecutionTargetSyncService(
    private val project: Project,
    private val scope: CoroutineScope,
) : Disposable {
    private var connection: MessageBusConnection? = null
    private var started = false

    fun start() {
        if (started || project.isDisposed) return
        started = true

        scope.launch {
            val runManager = RunManager.getInstanceAsync(project)
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) return@withContext

                val synchronizer = TargetSynchronizer(project, runManager)

                fun refreshTargets() {
                    ExecutionTargetManager.update(project)
                    synchronizer.syncTargetFromConfiguration(runManager.selectedConfiguration)
                }

                val connection = project.messageBus.connect(this@XMakeExecutionTargetSyncService).also {
                    this@XMakeExecutionTargetSyncService.connection = it
                }
                connection.subscribe(
                    RunManagerListener.TOPIC,
                    object : RunManagerListener {
                        override fun runConfigurationSelected(settings: RunnerAndConfigurationSettings?) {
                            synchronizer.syncTargetFromConfiguration(settings)
                        }
                    },
                )
                connection.subscribe(
                    ExecutionTargetManager.TOPIC,
                    ExecutionTargetListener(synchronizer::syncConfigurationFromTarget),
                )
                connection.subscribe(
                    XMakeBuildProfileManager.TOPIC,
                    XMakeBuildProfileManager.Listener { refreshTargets() },
                )
                connection.subscribe(
                    XMakeProjectDirectoryManager.TOPIC,
                    XMakeProjectDirectoryManager.Listener { refreshTargets() },
                )
                synchronizer.syncTargetFromConfiguration(runManager.selectedConfiguration)
            }
        }
    }

    override fun dispose() {
        connection?.disconnect()
        connection = null
    }
}

class XMakeExecutionTargetSyncActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(XMakeExecutionTargetSyncService::class.java)?.start()
    }
}

private class TargetSynchronizer(
    private val project: Project,
    private val runManager: RunManager,
) {
    private var trackedConfiguration: RunConfiguration? = null
    private var activeTargetUpdateDepth = 0
    private var lastProfileId: String? = null

    fun syncTargetFromConfiguration(settings: RunnerAndConfigurationSettings?) {
        val selectedConfiguration = settings?.configuration
        trackedConfiguration = selectedConfiguration
        val configuration = selectedConfiguration as? XMakeProfileRunConfiguration ?: return
        val activeTarget = ExecutionTargetManager.getActiveTarget(project)
        val profileTargets = ExecutionTargetManager.getTargetsToChooseFor(project, selectedConfiguration)
            .filterIsInstance<XMakeBuildProfileExecutionTarget>()
        val activeProfileId = (activeTarget as? XMakeBuildProfileExecutionTarget)?.profileId
        val targetProfileId = configuration.preferredBuildProfileId ?: activeProfileId
        val targetToActivate = targetProfileId
            ?.let { profileId -> profileTargets.firstOrNull { candidate -> candidate.profileId == profileId } }
            ?: profileTargets.singleOrNull()
        if (targetToActivate == null) {
            configuration.preferredBuildProfileId = null
            if (activeTarget is XMakeBuildProfileExecutionTarget) {
                switchActiveTarget(DefaultExecutionTarget.INSTANCE)
            }
            return
        }
        configuration.preferredBuildProfileId = targetToActivate.profileId
        if ((activeTarget as? XMakeBuildProfileExecutionTarget)?.profileId == targetToActivate.profileId) return

        switchActiveTarget(targetToActivate)
    }

    fun syncConfigurationFromTarget(executionTarget: ExecutionTarget) {
        if (activeTargetUpdateDepth > 0) return
        val profileId = (executionTarget as? XMakeBuildProfileExecutionTarget)?.profileId ?: return
        val configuration = runManager.selectedConfiguration
            ?.configuration as? XMakeProfileRunConfiguration ?: return
        if (configuration !== trackedConfiguration) return
        configuration.preferredBuildProfileId = profileId
        if (profileId == lastProfileId) return
        lastProfileId = profileId
        autoConfigure(profileId)
    }

    private fun autoConfigure(profileId: String) {
        val profile = project.xmakeBuildProfiles.findProfile(profileId) ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val task = try {
                XMakeBuildTask(
                    presentableName = "Configure '${profile.name}'",
                    commands = listOf(XMakeCommandFactory(project, profile).createConfigure()),
                )
            } catch (error: Exception) {
                notifyConfigureFailed(profile.name, error.message)
                return@executeOnPooledThread
            }
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    ProjectTaskManager.getInstance(project).run(task)
                        .onSuccess { result ->
                            if (result.hasErrors()) notifyConfigureFailed(profile.name, null)
                        }
                        .onError { error -> notifyConfigureFailed(profile.name, error.message) }
                }
            }
        }
    }

    private fun notifyConfigureFailed(profileName: String, details: String?) {
        if (project.isDisposed) return
        val message = buildString {
            append("Configure failed for profile '").append(profileName).append("'")
            if (!details.isNullOrBlank()) append(": ").append(details)
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("XMake.NotificationGroup")
            .createNotification(message, NotificationType.ERROR)
            .notify(project)
    }

    private fun switchActiveTarget(target: ExecutionTarget) {
        // The listener echo from our own switch must not write back into the configuration.
        (target as? XMakeBuildProfileExecutionTarget)?.let { lastProfileId = it.profileId }
        activeTargetUpdateDepth++
        try {
            ExecutionTargetManager.setActiveTarget(project, target)
        } finally {
            activeTargetUpdateDepth--
        }
    }
}
