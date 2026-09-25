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
package io.xmake.clion

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionTarget
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.xmake.build.XMakeBuildTask
import io.xmake.build.runXMakeBuildTask
import io.xmake.debug.resolveXMakeTargetLocation
import io.xmake.run.command.XMakeCommandFactory
import io.xmake.run.command.xmakeExecutionService
import io.xmake.run.target.findXMakeBuildProfileFor
import io.xmake.run.target.requireXMakeBuildProfileFor
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** An xmake target built for a CLion-launched run configuration. */
data class XMakeBuiltTarget(
    val executable: File,
    val workingDirectory: String,
)

/**
 * The public surface the CLion-only run configuration (`:clion-run`) uses; everything xmake-specific
 * (profiles, commands, the build task, target-path resolution) stays in root.
 */
object XMakeClionLaunchBridge {

    fun canRunOn(project: Project, target: ExecutionTarget): Boolean =
        project.findXMakeBuildProfileFor(target) != null

    /**
     * Configures and builds [targetName] for the profile selected by [executionTarget] through
     * CLion's Build tool window, then resolves and remembers the built executable. Blocks; call
     * off the EDT.
     */
    @RequiresBackgroundThread
    fun buildAndResolve(project: Project, executionTarget: ExecutionTarget, targetName: String): XMakeBuiltTarget {
        val profile = project.requireXMakeBuildProfileFor(executionTarget)
        val factory = XMakeCommandFactory(project, profile)
        val build = factory.createTargetBuild(targetName)
        if (build.toolkit.requiresBackend) {
            throw ExecutionException("CLion can only launch XMake targets built by a local toolkit")
        }
        val built = runBlockingMaybeCancellable {
            runXMakeBuildTask(
                project,
                XMakeBuildTask("Build '$targetName'", listOf(factory.createConfigure(), build)),
            )
            resolve(project, factory, targetName)
        }
        project.service<XMakeBuiltTargetCache>().put(profile.id, targetName, built)
        return built
    }

    /**
     * The executable [buildAndResolve] last produced for this profile and target. Keyed by
     * profile/target rather than by [com.intellij.execution.runners.ExecutionEnvironment]: CLion's
     * debug-profile runner launches with a different environment than the before-launch step saw.
     * Falls back to querying xmake (blocking) when nothing was built in this session.
     */
    fun resolveBuilt(project: Project, executionTarget: ExecutionTarget, targetName: String): XMakeBuiltTarget {
        val profile = project.requireXMakeBuildProfileFor(executionTarget)
        project.service<XMakeBuiltTargetCache>().get(profile.id, targetName)
            ?.takeIf { it.executable.isFile }
            ?.let { return it }
        return runBlockingMaybeCancellable { resolve(project, XMakeCommandFactory(project, profile), targetName) }
    }

    private suspend fun resolve(project: Project, factory: XMakeCommandFactory, targetName: String): XMakeBuiltTarget {
        val query = factory.createTargetPathQuery(targetName)
        val execution = project.xmakeExecutionService
        val output = execution.runExclusive { execution.captureStandardOutput(query) }
        val location = resolveXMakeTargetLocation(targetName, output, query.workingDirectory)
        return XMakeBuiltTarget(
            executable = location.executableFile,
            workingDirectory = location.effectiveRunDirectory.path,
        )
    }
}

@Service(Service.Level.PROJECT)
internal class XMakeBuiltTargetCache {
    private val targets = ConcurrentHashMap<Pair<String, String>, XMakeBuiltTarget>()

    fun put(profileId: String, targetName: String, target: XMakeBuiltTarget) {
        targets[profileId to targetName] = target
    }

    fun get(profileId: String, targetName: String): XMakeBuiltTarget? = targets[profileId to targetName]
}
