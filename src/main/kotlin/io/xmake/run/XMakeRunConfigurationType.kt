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
 * @author      ruki
 * @file        XMakeRunConfigurationType.kt
 *
 */
package io.xmake.run

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import io.xmake.icons.XMakeIcons

class XMakeRunConfigurationType : ConfigurationTypeBase(
    "XMakeRunConfiguration",
    "XMake",
    "XMake run command configuration",
    XMakeIcons.XMAKE
) {
    init {
        addFactory(object : ConfigurationFactory(this) {
            override fun createTemplateConfiguration(project: Project): RunConfiguration =
                XMakeRunConfiguration(project, "XMake", this)

            // This value gets written to the config file. By default it defers to getName, however,
            // the value needs to be kept the same even if the display name changes in the future
            // in order to maintain compatibility with older configs.
            override fun getId() = "Start and Debug"
        })
    }

    val factory: ConfigurationFactory get() = configurationFactories.single()

    companion object {
        fun getInstance(): XMakeRunConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(XMakeRunConfigurationType::class.java)
    }
}
