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
package io.xmake.project.profile

import com.intellij.execution.configurations.RuntimeConfigurationError
import com.intellij.openapi.project.Project
import com.intellij.util.IncorrectOperationException
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.project.toolkit.ToolkitManager
import io.xmake.utils.path.WorkingDirectoryResolver
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.UUID

/** Persisted project build inputs shared by Build, Run, and Debug. */
data class XMakeBuildProfile(
    // Serialized by XMLB; profile editors never change this identity.
    var id: String = UUID.randomUUID().toString(),
    var name: String = DEFAULT_PROFILE_NAME,
    var toolkitId: String? = null,
    var platform: String = USE_XMAKE_DEFAULT,
    var architecture: String = USE_XMAKE_DEFAULT,
    var toolchain: String = USE_XMAKE_DEFAULT,
    var buildMode: String = DEFAULT_BUILD_MODE,
    var workingDirectory: String = "",
    var buildDirectory: String = "",
    var androidNdkDirectory: String = "",
    var verbose: Boolean = false,
    var configureArguments: String = "",
) {
    internal fun resolveToolkit(project: Project): Toolkit? =
        toolkitId?.let { id -> ToolkitManager.getInstance().registeredToolkit(id, project) }

    internal fun canExecute(project: Project): Boolean {
        if (!isValidId(id)) return false
        val toolkit = resolveToolkit(project) ?: return false
        return toolkit.isAvailable &&
                toolkit.path.isNotBlank() &&
                workingDirectory.isNotBlank() &&
                (!toolkit.requiresBackend || toolkit.host.hasBackend)
    }

    internal fun resolveWorkingDirectory(project: Project, toolkit: Toolkit): String {
        if (toolkit.path.isBlank()) {
            throw RuntimeConfigurationError("XMake toolkit path is not set")
        }
        if (toolkit.requiresBackend && !toolkit.host.hasBackend) {
            throw RuntimeConfigurationError("XMake ${toolkit.host.type} toolkit host is not available")
        }
        if (workingDirectory.isBlank()) {
            throw RuntimeConfigurationError("Working directory is not set")
        }
        val resolvedWorkingDirectory = try {
            when (toolkit.host.type) {
                ToolkitHostType.LOCAL ->
                    WorkingDirectoryResolver.resolve(project, workingDirectory, validation = true)

                else -> WorkingDirectoryResolver.resolve(project, workingDirectory, toolkit)
            }
        } catch (error: IncorrectOperationException) {
            throw RuntimeConfigurationError(error.message ?: "Working directory contains invalid macros")
        }
        if (toolkit.host.type != ToolkitHostType.LOCAL) {
            if (!resolvedWorkingDirectory.startsWith('/')) {
                throw RuntimeConfigurationError(
                    "XMake ${toolkit.host.type} working directory must be an absolute host path",
                )
            }
            return resolvedWorkingDirectory
        }

        val path = try {
            Path.of(resolvedWorkingDirectory)
        } catch (_: InvalidPathException) {
            throw RuntimeConfigurationError("Working directory is invalid: $resolvedWorkingDirectory")
        }
        if (!Files.isDirectory(path)) {
            throw RuntimeConfigurationError("Working directory does not exist: $resolvedWorkingDirectory")
        }
        return resolvedWorkingDirectory
    }

    companion object {
        /** Placeholder value meaning "use the xmake default" for platform/architecture/toolchain. */
        const val USE_XMAKE_DEFAULT = "default"

        const val DEFAULT_PROFILE_NAME = "Default"

        const val DEFAULT_BUILD_MODE = "release"

        /** "Name", "Name 2", "Name 3", ... — the first candidate not present in [existingNames]. */
        internal fun uniqueName(requestedName: String, existingNames: Collection<String>): String {
            val base = requestedName.trim().ifBlank { DEFAULT_PROFILE_NAME }
            return sequenceOf(base)
                .plus(generateSequence(2) { it + 1 }.map { suffix -> "$base $suffix" })
                .first { candidate -> candidate !in existingNames }
        }

        /** The default profile name ("Default"), made unique among [existingNames]. */
        internal fun uniqueDefaultName(existingNames: Collection<String>): String =
            uniqueName(DEFAULT_PROFILE_NAME, existingNames)

        internal fun createDefault(project: Project, name: String = DEFAULT_PROFILE_NAME): XMakeBuildProfile {
            val manager = ToolkitManager.getInstance()
            val registeredToolkits = manager.registeredToolkits(project)
            val toolkit = manager.defaultToolkitId
                ?.let { id -> registeredToolkits.firstOrNull { toolkit -> toolkit.id == id } }
                ?: registeredToolkits.firstOrNull()
            return XMakeBuildProfile(
                name = name,
                toolkitId = toolkit?.id,
                workingDirectory = if (toolkit?.host?.type == ToolkitHostType.SSH) {
                    ""
                } else {
                    project.basePath.orEmpty()
                },
            )
        }

        internal fun isValidId(id: String): Boolean = PROFILE_ID_PATTERN.matches(id)

        /** Drops malformed/duplicate records and gives every remaining profile a unique name. */
        internal fun normalize(profiles: Iterable<XMakeBuildProfile>): List<XMakeBuildProfile> {
            val usedIds = mutableSetOf<String>()
            val usedNames = mutableSetOf<String>()
            return profiles.mapNotNull { profile ->
                if (!isValidId(profile.id) || !usedIds.add(profile.id)) return@mapNotNull null
                val name = uniqueName(profile.name, usedNames)
                usedNames += name
                profile.copy(name = name)
            }
        }

        private val PROFILE_ID_PATTERN = Regex("[A-Za-z0-9_-]+")
    }
}
