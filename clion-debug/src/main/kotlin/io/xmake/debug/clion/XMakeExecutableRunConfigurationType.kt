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
 * @file        XMakeExecutableRunConfigurationType.kt
 *
 */
package io.xmake.debug.clion

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyValue
import com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfiguration

/**
 * A branded, CLion-native run configuration type for xmake executable targets. Its factory produces
 * CLion's own [CLionExternalRunConfiguration] (bound to an xmake Custom Build Target, see
 * [CustomBuildTargetsIntegration]), so it inherits native Build / Run / Debug, the "build the target"
 * before-run step, and the standard target+executable settings editor — while showing up under a
 * distinct "Xmake Executable" name.
 *
 * CLion-only; registered dynamically from the reflectively-loaded module (see [XMakeRunConfigRegistrar])
 * so the main plugin still loads on IDEA Community.
 */
class XMakeExecutableRunConfigurationType : ConfigurationTypeBase(
    ID,
    DISPLAY_NAME,
    "Build, run and debug an xmake executable target natively",
    NotNullLazyValue.createValue { AllIcons.RunConfigurations.Application }
) {
    init {
        addFactory(XMakeExecutableConfigurationFactory(this))
    }

    companion object {
        const val ID = "io.xmake.XMakeExecutable"
        const val DISPLAY_NAME = "Xmake Executable"
    }
}

class XMakeExecutableConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = XMakeExecutableRunConfigurationType.DISPLAY_NAME

    override fun createTemplateConfiguration(project: Project): RunConfiguration =
        CLionExternalRunConfiguration(project, this, XMakeExecutableRunConfigurationType.DISPLAY_NAME)
}
