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
 * @file        XMakeRunConfigurationProducer.kt
 *
 */
package io.xmake.run

import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import io.xmake.project.directory.hasXMakeProjectDirectorySource
import io.xmake.run.command.DEFAULT_BUILD_TARGET

class XMakeRunConfigurationProducer : LazyRunConfigurationProducer<XMakeRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory {
        return XMakeRunConfigurationType.getInstance().factory
    }

    override fun isConfigurationFromContext(
        configuration: XMakeRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        // A context gesture produces a configuration with template defaults only, so an existing
        // configuration that still matches those defaults can be reused. preferredBuildProfileId
        // is realigned by the target synchronizer, and debug-only fields do not affect run semantics.
        return context.project.hasXMakeProjectDirectorySource &&
                configuration.runTarget == DEFAULT_BUILD_TARGET &&
                configuration.runArguments.isEmpty() &&
                configuration.launchWorkingDirectory.isEmpty() &&
                configuration.runEnvironment == EnvironmentVariablesData.DEFAULT
    }

    override fun setupConfigurationFromContext(
        configuration: XMakeRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        // A configured nested root is as valid as an xmake.lua at the IDE project root.
        return context.project.hasXMakeProjectDirectorySource
    }
}
