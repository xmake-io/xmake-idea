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
 * @file        XMakeProjectTaskRunner.kt
 *
 */
package io.xmake.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.openapi.project.Project
import com.intellij.task.BuildTask
import com.intellij.task.ProjectTask
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskRunner
import io.xmake.shared.xmakeConfiguration
import io.xmake.shared.xmakeConfigurationOrNull
import io.xmake.utils.SystemUtils
import io.xmake.utils.exception.XMakeRunConfigurationNotSetException
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

/**
 * Routes the IDE's native Build action (Build Project / Ctrl+F9) to xmake for xmake projects, so
 * users get native build without going through the XMake menu. Reconfigures (`xmake f`) if the
 * configuration changed, then runs `xmake build`.
 */
class XMakeProjectTaskRunner : ProjectTaskRunner() {

    override fun canRun(projectTask: ProjectTask): Boolean = projectTask is BuildTask

    override fun canRun(project: Project, projectTask: ProjectTask): Boolean =
        projectTask is BuildTask && SystemUtils.isXMakeProject(project)

    override fun run(
        project: Project,
        context: ProjectTaskContext,
        vararg tasks: ProjectTask,
    ): Promise<Result> {
        if (project.xmakeConfigurationOrNull == null) {
            return resolvedPromise(result(aborted = true, errors = false))
        }
        val promise = AsyncPromise<Result>()
        try {
            val xmakeConfiguration = project.xmakeConfiguration

            fun build() {
                val handler = SystemUtils.runvInConsole(
                    project, xmakeConfiguration.buildCommandLine, true, true, true
                )
                if (handler == null) {
                    promise.setResult(result(aborted = false, errors = false))
                } else {
                    handler.addProcessListener(object : ProcessListener {
                        override fun processTerminated(e: ProcessEvent) {
                            promise.setResult(result(aborted = false, errors = e.exitCode != 0))
                        }
                    })
                }
            }

            if (xmakeConfiguration.changed) {
                val configureHandler =
                    SystemUtils.runvInConsole(project, xmakeConfiguration.configurationCommandLine)
                if (configureHandler == null) {
                    build()
                } else {
                    configureHandler.addProcessListener(object : ProcessListener {
                        override fun processTerminated(e: ProcessEvent) {
                            if (e.exitCode == 0) {
                                xmakeConfiguration.changed = false
                                build()
                            } else {
                                promise.setResult(result(aborted = false, errors = true))
                            }
                        }
                    })
                }
            } else {
                build()
            }
        } catch (e: XMakeRunConfigurationNotSetException) {
            // No xmake run configuration selected — nothing to build.
            promise.setResult(result(aborted = true, errors = false))
        }
        return promise
    }

    private fun result(aborted: Boolean, errors: Boolean): Result = object : Result {
        override fun isAborted(): Boolean = aborted
        override fun hasErrors(): Boolean = errors
    }
}
