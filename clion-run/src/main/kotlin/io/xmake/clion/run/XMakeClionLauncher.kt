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

import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.jetbrains.cidr.cpp.execution.CLionLauncher
import com.jetbrains.cidr.cpp.toolchains.CPPEnvironment
import com.jetbrains.cidr.cpp.toolchains.CPPToolchains
import io.xmake.clion.XMakeBuiltTarget
import io.xmake.clion.XMakeClionLaunchBridge
import java.io.File
import java.nio.file.Path

/**
 * Supplies CLion with the binary xmake built. Everything else — process creation, and which
 * debugger to use (the active debug profile) — is inherited from [CLionLauncher].
 */
internal class XMakeClionLauncher(
    environment: ExecutionEnvironment,
    private val xmakeConfiguration: XMakeClionRunConfiguration,
) : CLionLauncher(environment, xmakeConfiguration) {

    override fun getRunFileAndEnvironment(): Pair<File, CPPEnvironment> {
        val built = builtTarget()
        // A CLion toolchain only supplies the debuggee's environment; the debug profile picks the debugger.
        val toolchain = CPPToolchains.getInstance().defaultToolchain
            ?: throw ExecutionException(
                "CLion has no default toolchain. Configure one in Settings | Build, Execution, Deployment | Toolchains.",
            )
        return built.executable to CPPEnvironment(toolchain)
    }

    // Match the other XMake launch paths: run from the xmake working directory, not the binary's folder.
    override fun getDefaultWorkingDir(executable: Path): String = builtTarget().workingDirectory

    // Built by XMakeClionBuildBeforeRunTaskProvider; CLion's profile runner hands us a different
    // ExecutionEnvironment than that step saw, so the bridge looks it up by profile + target.
    private fun builtTarget(): XMakeBuiltTarget =
        XMakeClionLaunchBridge.resolveBuilt(project, executionEnvironment.executionTarget, xmakeConfiguration.runTarget)
}
