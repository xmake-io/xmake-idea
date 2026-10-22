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
 * @file        XMakeRunConfiguration.kt
 *
 */
package io.xmake.run

import com.intellij.execution.Executor
import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTarget
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient
import io.xmake.migration.readLegacyBuildSettings
import io.xmake.migration.removeLegacyBuildSettings
import io.xmake.project.profile.xmakeBuildProfiles
import io.xmake.project.directory.xmakeProjectDirectories
import io.xmake.run.state.XMakeDebugState
import io.xmake.run.state.XMakeRunState
import io.xmake.utils.cli.DEFAULT_TARGET
import io.xmake.run.target.findXMakeBuildProfileFor
import io.xmake.utils.cli.ALL_TARGET
import java.io.File
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jdom.Element

class XMakeRunConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory,
) : LocatableConfigurationBase<RunProfileState>(project, factory, name),
    RunConfigurationWithSuppressedDefaultDebugAction,
    XMakeProfileRunConfiguration {

    @OptionTag(tag = "runTarget")
    override var runTarget: String = DEFAULT_TARGET

    @OptionTag(tag = "arguments")
    var runArguments: String = ""

    /**
     * Optional cwd for the launched target. Empty means use xmake's effective run directory
     * (`set_rundir()` or the target file directory).
     */
    @OptionTag(tag = "launchWorkingDirectory")
    var launchWorkingDirectory: String = ""

    /** Preferred profile reference; the execution target remains the runtime authority. */
    @OptionTag(tag = "buildProfile")
    override var preferredBuildProfileId: String? = null

    @get:Transient
    var runEnvironment: EnvironmentVariablesData = EnvironmentVariablesData.DEFAULT

    /** false (default): debug via CLion's own native GDB/LLDB driver. true: spawn an external DAP driver process. */
    @OptionTag(tag = "useDapDriver")
    var useDapDriver: Boolean = false

    @OptionTag(tag = "dapDriverPath")
    var dapDriverPath: String = ""

    @OptionTag(tag = "dapDriverAutoDetect")
    var dapDriverAutoDetect: Boolean = true

    @OptionTag(tag = "launchConfiguration")
    var launchConfiguration: String = defaultLaunchConfigJson()

    override fun writeExternal(element: Element) {
        super.writeExternal(element)

        removeLegacyBuildSettings(element)
        XmlSerializer.serializeInto(this, element)
        runEnvironment.writeExternal(element)
    }

    override fun readExternal(element: Element) {
        val legacyTarget = takeLegacyTargetTag(element)
        super.readExternal(element)

        XmlSerializer.deserializeInto(this, element)
        normalizeDeserializedStrings()
        if (runTarget == DEFAULT_TARGET && !legacyTarget.isNullOrBlank()) runTarget = legacyTarget
        runEnvironment = EnvironmentVariablesData.readExternal(element)
        migrateLegacyBuildSettings(element)
    }

    private fun takeLegacyTargetTag(element: Element): String? {
        val legacyTarget = element.getChildren("target")
            .firstOrNull { it.getAttributeValue("name") == "runTarget" }
            ?.getAttributeValue("value")
        element.removeChildren("target")
        return legacyTarget
    }

    private fun migrateLegacyBuildSettings(element: Element) {
        val migrated = readLegacyBuildSettings(element, name) ?: return
        migrated.legacyProjectDirectory?.let { legacyProjectDirectory ->
            project.xmakeProjectDirectories.migrateLegacyProjectDirectories(listOf(legacyProjectDirectory))
        }
        if (preferredBuildProfileId?.let(project.xmakeBuildProfiles::findProfile) == null) {
            preferredBuildProfileId = project.xmakeBuildProfiles.importMigratedProfile(migrated.profile).id
        }
    }

    override fun checkConfiguration() {
        // `xmake run` binds the first positional argument to the target name, so program
        // arguments cannot reach the target unless a concrete one is selected.
        val target = runTarget.trim()
        val needsConcreteTarget = runArguments.isNotBlank() &&
                (target.isEmpty() || target == DEFAULT_TARGET || target == ALL_TARGET)
        if (needsConcreteTarget) {
            throw RuntimeConfigurationError("Program arguments require a concrete run target, got '$target'")
        }
        if (useDapDriver && !dapDriverAutoDetect) {
            val driverPath = dapDriverPath.trim()
            if (driverPath.isEmpty() || !File(driverPath).canExecute()) {
                throw RuntimeConfigurationError("DAP driver path does not point to an executable: '$driverPath'")
            }
        }
        if (launchConfiguration.isNotBlank()) {
            try {
                Json.decodeFromString<JsonObject>(launchConfiguration)
            } catch (error: SerializationException) {
                throw RuntimeConfigurationError("Launch configuration is not a valid JSON object: ${error.message}")
            }
        }
    }

    /** The XML serializer can leave a non-null String null; restore every default. */
    @Suppress("USELESS_ELVIS")
    private fun normalizeDeserializedStrings() {
        runTarget = runTarget ?: DEFAULT_TARGET
        runArguments = runArguments ?: ""
        launchWorkingDirectory = launchWorkingDirectory ?: ""
        dapDriverPath = dapDriverPath ?: ""
        launchConfiguration = launchConfiguration ?: defaultLaunchConfigJson()
    }

    override fun canRunOn(target: ExecutionTarget): Boolean = project.findXMakeBuildProfileFor(target) != null

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        XMakeRunConfigurationEditor(project)

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState =
        when (executor.id) {
            DefaultDebugExecutor.EXECUTOR_ID -> XMakeDebugState.create(this, environment)
            DefaultRunExecutor.EXECUTOR_ID -> XMakeRunState.create(this, environment)
            else -> throw ExecutionException("Unsupported XMake executor: ${executor.id}")
        }

    companion object {
        /** Only keys documented for gdb's DAP interpreter and lldb-dap belong here; the
         *  object-form cpptools leftovers (sourceMap objects, setupCommands, variables) are
         *  not read by either. lldb-dap's documented array-form sourceMap stays passable. */
        fun defaultGdbLaunchConfigJson(): String {
            return """{
    "stopOnEntry": true
}"""
        }

        fun defaultLldbLaunchConfigJson(): String {
            return """{
    "stopOnEntry": true,
    "initCommands": []
}"""
        }

        fun defaultLaunchConfigJson(): String = defaultLldbLaunchConfigJson()
    }
}
