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
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import io.xmake.utils.SystemUtils

class XMakeRunConfigurationProducer : LazyRunConfigurationProducer<XMakeRunConfiguration>() {
    override fun getConfigurationFactory(): ConfigurationFactory {
        return XMakeRunConfigurationType.getInstance().factory
    }

    override fun isConfigurationFromContext(
            configuration: XMakeRunConfiguration,
            context: ConfigurationContext
    ): Boolean {

        return false
    }

    override fun setupConfigurationFromContext(
            configuration: XMakeRunConfiguration,
            context: ConfigurationContext,
            sourceElement: Ref<PsiElement>
    ): Boolean {

        // check xmake project
        if (!SystemUtils.isXMakeProject(context.project)) {
            return false
        }

        return true
    }
}
