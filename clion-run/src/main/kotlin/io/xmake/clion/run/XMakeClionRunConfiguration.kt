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

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.jetbrains.cidr.cpp.execution.CLionRunConfiguration
import com.jetbrains.cidr.execution.CidrBuildConfiguration
import com.jetbrains.cidr.execution.CidrBuildConfigurationHelper
import com.jetbrains.cidr.execution.CidrBuildTarget
import com.jetbrains.cidr.execution.CidrCommandLineState
import com.jetbrains.cidr.execution.ExecutableData
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration
import io.xmake.clion.XMakeClionLaunchBridge
import io.xmake.run.XMakeProfileRunConfiguration
import io.xmake.run.command.DEFAULT_BUILD_TARGET
import org.jdom.Element

private typealias NoBuildTarget = CidrBuildTarget<CidrBuildConfiguration>

/**
 * A [CLionRunConfiguration] without a CLion build model: xmake builds the target (a before-launch
 * step) and CLion's own runner launches/debugs the result, so the active debug profile applies.
 */
class XMakeClionRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String,
) : CLionRunConfiguration<CidrBuildConfiguration, NoBuildTarget>(project, factory, name),
    XMakeProfileRunConfiguration {

    override var runTarget: String = DEFAULT_BUILD_TARGET

    override var preferredBuildProfileId: String? = null

    // The executable comes from xmake on each launch (XMakeClionLauncher), never from stored data.
    private var executableData: ExecutableData? = null

    override fun getExecutableData(): ExecutableData? = executableData

    override fun setExecutableData(data: ExecutableData?) {
        executableData = data
    }

    override fun getHelper(): CidrBuildConfigurationHelper<CidrBuildConfiguration, NoBuildTarget> = NoBuildTargets

    // No CLion resolve configuration: xmake projects feed IntelliSense via the compilation database.
    override fun getResolveConfiguration(target: ExecutionTarget): OCResolveConfiguration? = null

    override fun canRunOn(target: ExecutionTarget): Boolean = XMakeClionLaunchBridge.canRunOn(project, target)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): CommandLineState =
        CidrCommandLineState(environment, XMakeClionLauncher(environment, this))

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = XMakeClionRunConfigurationEditor(project)

    override fun checkConfiguration() {
        if (runTarget.isBlank()) throw RuntimeConfigurationError("XMake target is not set")
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        runTarget = JDOMExternalizerUtil.readField(element, TARGET_FIELD) ?: DEFAULT_BUILD_TARGET
        preferredBuildProfileId = JDOMExternalizerUtil.readField(element, PROFILE_FIELD)
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, TARGET_FIELD, runTarget)
        preferredBuildProfileId?.let { JDOMExternalizerUtil.writeField(element, PROFILE_FIELD, it) }
    }

    private object NoBuildTargets : CidrBuildConfigurationHelper<CidrBuildConfiguration, NoBuildTarget>() {
        override fun getTargets(): List<NoBuildTarget> = emptyList()
    }

    private companion object {
        const val TARGET_FIELD = "xmakeTarget"
        const val PROFILE_FIELD = "buildProfile"
    }
}
