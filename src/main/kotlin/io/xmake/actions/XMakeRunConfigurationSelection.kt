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
package io.xmake.actions

import com.intellij.execution.Executor
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import io.xmake.utils.notify
import io.xmake.run.XMakeRunConfiguration

internal fun launchSelectedXMakeRunConfiguration(project: Project, executor: Executor) {
    val settings = project.selectedXMakeRunConfigurationSettings
    if (settings == null) {
        project.notify("Please select a valid XMake configuration first", NotificationType.WARNING)
        return
    }

    ProgramRunnerUtil.executeConfiguration(settings, executor)
}

/** The selected XMake run configuration without creating the run manager; action updates run on
 *  a background thread and must not trigger its initialization. */
internal val Project.selectedXMakeRunConfigurationIfCreated: XMakeRunConfiguration?
    get() = RunManager.getInstanceIfCreated(this)
        ?.selectedConfiguration
        ?.configuration
        as? XMakeRunConfiguration

private val Project.selectedXMakeRunConfigurationSettings: RunnerAndConfigurationSettings?
    get() = RunManager.getInstance(this).selectedConfiguration
        ?.takeIf { it.configuration is XMakeRunConfiguration }
