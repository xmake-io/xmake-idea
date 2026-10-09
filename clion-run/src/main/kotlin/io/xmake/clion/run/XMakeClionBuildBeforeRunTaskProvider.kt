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
package io.xmake.clion.run

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Key
import io.xmake.clion.XMakeClionLaunchBridge
import io.xmake.icons.XMakeIcons
import javax.swing.Icon

class XMakeClionBuildBeforeRunTask : BeforeRunTask<XMakeClionBuildBeforeRunTask>(XMakeClionBuildBeforeRunTaskProvider.ID)

/**
 * Builds the configuration's xmake target (in CLion's Build tool window); the bridge remembers the
 * resulting executable for [XMakeClionLauncher]. Before-launch steps run off the EDT, so blocking is fine.
 */
class XMakeClionBuildBeforeRunTaskProvider : BeforeRunTaskProvider<XMakeClionBuildBeforeRunTask>() {

    override fun getId(): Key<XMakeClionBuildBeforeRunTask> = ID

    override fun getName(): String = "Build XMake target"

    override fun getIcon(): Icon = XMakeIcons.XMAKE

    override fun createTask(runConfiguration: RunConfiguration): XMakeClionBuildBeforeRunTask? =
        if (runConfiguration is XMakeClionRunConfiguration) XMakeClionBuildBeforeRunTask().apply { isEnabled = true } else null

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        environment: ExecutionEnvironment,
        task: XMakeClionBuildBeforeRunTask,
    ): Boolean {
        val xmake = configuration as? XMakeClionRunConfiguration ?: return false
        return try {
            XMakeClionLaunchBridge.buildAndResolve(environment.project, environment.executionTarget, xmake.runTarget)
            true
        } catch (error: ProcessCanceledException) {
            throw error
        } catch (error: Exception) {
            LOG.warn("XMake build step failed for '${xmake.runTarget}'", error)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("XMake.NotificationGroup")
                .createNotification(error.message ?: "XMake build failed", NotificationType.ERROR)
                .notify(environment.project)
            false
        }
    }

    companion object {
        val ID: Key<XMakeClionBuildBeforeRunTask> = Key.create("XMake.Clion.Build")
        private val LOG = logger<XMakeClionBuildBeforeRunTaskProvider>()
    }
}
