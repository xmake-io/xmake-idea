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
 * @file        XMakeReconfigure.kt
 *
 */
package io.xmake.shared

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import io.xmake.debug.CompDBSupport
import io.xmake.debug.CustomBuildTargetsSupport
import io.xmake.project.xmakeSettings
import io.xmake.utils.SystemUtils

/**
 * Project-level reconfigure / compile-commands helpers driven by the project-level xmake
 * configuration ([io.xmake.project.XMakeSettings]) rather than a selected run configuration, so they
 * work with the native "Xmake Executable" run config selected. Shared by the toolbar mode dropdown
 * and the XMake Config tool window.
 */
object XMakeReconfigure {

    /**
     * Run `xmake f …` for the current project-level configuration, streaming to the XMake console.
     * On success, regenerates `compile_commands.json` + refreshes IntelliSense when
     * `autoUpdateCompileCommands` is on.
     */
    fun reconfigure(project: Project) {
        val configuration = project.xmakeConfigurationOrNull ?: return
        configuration.changed = true
        val handler = SystemUtils.runvInConsole(project, configuration.configurationCommandLine)
        if (handler == null) {
            configuration.changed = false
            return
        }
        handler.addProcessListener(object : ProcessListener {
            override fun processTerminated(e: ProcessEvent) {
                if (e.exitCode == 0) {
                    configuration.changed = false
                    // xmake encodes the build mode in each target's output path, so a reconfigure
                    // moves the binaries (e.g. release/ -> debug/). Re-sync the CLion build targets so
                    // the native "Xmake Executable" run configs point at the freshly-configured binary
                    // (with symbols) instead of a stale one — otherwise the debugger loads no symbols.
                    // Path resolution shells out per target, so keep it off the callback thread.
                    ApplicationManager.getApplication().executeOnPooledThread {
                        CustomBuildTargetsSupport.syncTargets(project)
                    }
                    if (project.xmakeSettings.state.autoUpdateCompileCommands) {
                        generateCompileCommands(project)
                    }
                }
            }
        })
    }

    /** Run `xmake project -k compile_commands …` and, on success, refresh CLion IntelliSense. */
    fun generateCompileCommands(project: Project) {
        val configuration = project.xmakeConfigurationOrNull ?: return
        SystemUtils.runvInConsole(project, configuration.updateCompileCommandsLine, false, true, true)
            ?.addProcessListener(object : ProcessListener {
                override fun processTerminated(e: ProcessEvent) {
                    if (e.exitCode == 0) CompDBSupport.refreshIntelliSense(project)
                }
            })
    }
}
