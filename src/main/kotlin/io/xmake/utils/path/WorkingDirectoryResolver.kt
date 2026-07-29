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
package io.xmake.utils.path

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.execution.util.ProgramParametersConfigurator
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import java.nio.file.InvalidPathException
import java.nio.file.Path

object WorkingDirectoryResolver {
    fun resolve(project: Project, workingDirectory: String): String =
        resolve(project, workingDirectory, validation = false)

    fun resolve(project: Project, workingDirectory: String, validation: Boolean): String =
        ProgramParametersConfigurator().apply { setValidation(validation) }
            .expandPathAndMacros(workingDirectory, null, project)
            ?: workingDirectory

    fun resolve(project: Project, workingDirectory: String, toolkit: Toolkit): String =
        when (toolkit.host.type) {
            ToolkitHostType.LOCAL -> resolve(project, workingDirectory)
            ToolkitHostType.WSL -> {
                val distribution = toolkit.host.target as? WSLDistribution
                    ?: throw RuntimeConfigurationError("XMake WSL toolkit host is not available")
                resolveForWsl(project, workingDirectory, distribution)
            }
            ToolkitHostType.SSH -> workingDirectory
        }

    fun resolveForWsl(
        project: Project,
        workingDirectory: String,
        distribution: WSLDistribution,
    ): String = convertToWslPath(resolve(project, workingDirectory), distribution::getWslPath)

    internal fun convertToWslPath(
        workingDirectory: String,
        converter: (Path) -> String?,
    ): String {
        if (
            !OSAgnosticPathUtil.isAbsoluteDosPath(workingDirectory) &&
            !OSAgnosticPathUtil.isUncPath(workingDirectory)
        ) {
            return workingDirectory
        }
        val path = try {
            Path.of(workingDirectory)
        } catch (_: InvalidPathException) {
            return workingDirectory
        }
        return converter(path) ?: workingDirectory
    }
}
