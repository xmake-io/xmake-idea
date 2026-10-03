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
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import io.xmake.icons.XMakeIcons

/** CLion-only XMake configuration: debugged by CLion's own runner, so debug profiles apply. */
class XMakeClionRunConfigurationType : ConfigurationTypeBase(
    ID,
    "XMake Application (CLion)",
    "Build an xmake target and run or debug it with CLion's debugger and debug profiles",
    XMakeIcons.XMAKE,
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun getId(): String = ID

            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                XMakeClionRunConfiguration(project, this, "XMake")

            override fun configureBeforeRunTaskDefaults(
                providerID: Key<out BeforeRunTask<BeforeRunTask<*>>>,
                task: BeforeRunTask<out BeforeRunTask<*>>,
            ) {
                // CLion's own build steps expect CLion build targets, which this configuration has none of.
                task.isEnabled = providerID == XMakeClionBuildBeforeRunTaskProvider.ID
            }
        })
    }

    companion object {
        const val ID = "XMakeClionRunConfiguration"
    }
}
