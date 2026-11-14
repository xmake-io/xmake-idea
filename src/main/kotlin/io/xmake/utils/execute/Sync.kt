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
 * @author      windchargerj
 * @file        Sync.kt
 *
 */
package io.xmake.utils.execute

import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.utils.extension.ToolkitHostExtension
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path as NioPath
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

enum class SyncDirection { LOCAL_TO_REMOTE, REMOTE_TO_LOCAL }

internal val defaultSyncExcludedEntryNames = setOf(".xmake", ".idea", "build", ".git", ".gitignore")

/** Refreshes only the synced local directory instead of the whole VFS, and still completes
 *  after a cancellation so freshly fetched files are immediately visible. */
private suspend fun refreshVirtualFileSystem(localDirectoryPath: String) {
    withContext(NonCancellable + Dispatchers.EDT) {
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(localDirectoryPath))?.let { localDirectory ->
            VfsUtil.markDirtyAndRefresh(false, true, true, localDirectory)
        }
    }
}

/** Transfers the project to or from [toolkit]'s build host.
 *
 *  Only SSH needs a transfer: local and WSL toolkits work on the same files as the IDE. A WSL
 *  distribution mounts the local project at its `/mnt` path (and a project inside the
 *  distribution is reachable from Windows through its `\\wsl$` path), so both sides already see
 *  the project in place and anything copied would be a second, divergent copy. */
suspend fun transferProjectFiles(
    project: Project,
    toolkit: Toolkit,
    direction: SyncDirection,
    hostDirectoryPath: String,
    localDirectoryPath: String,
) {
    if (project.isDisposed) {
        throw ProcessCanceledException()
    }
    require(hostDirectoryPath.isNotBlank()) { "Sync directory must be explicit" }
    require(localDirectoryPath.isNotBlank()) { "Local sync directory must be explicit" }

    if (toolkit.host.type != ToolkitHostType.SSH) return

    try {
        ToolkitHostExtension.requireForHostType(ToolkitHostType.SSH)
            .syncProject(toolkit.host, direction, normalizeRemotePath(hostDirectoryPath), localDirectoryPath)
    } finally {
        refreshVirtualFileSystem(localDirectoryPath)
    }
}

/** Lexically normalizes a POSIX path: collapses '//', '.' and '..' segments. */
internal fun normalizeRemotePath(path: String): String {
    val absolute = path.startsWith("/")
    val segments = ArrayDeque<String>()
    for (segment in path.split('/')) {
        when {
            segment.isEmpty() || segment == "." -> Unit
            segment == ".." -> segments.removeLastOrNull()
            else -> segments.addLast(segment)
        }
    }
    val joined = segments.joinToString("/")
    return when {
        absolute && joined.isEmpty() -> "/"
        absolute -> "/$joined"
        else -> joined
    }
}

/** Whether this path is [root] itself or located underneath it. */
internal fun String.isWithin(root: String): Boolean =
    this == root || (root == "/" && startsWith("/")) || startsWith("$root/")

internal fun resolveSshSyncPath(hostDirectoryPath: String, relativePath: String?): String {
    require(hostDirectoryPath.isNotBlank()) { "Sync directory must be explicit" }
    if (relativePath == null) return normalizeRemotePath(hostDirectoryPath)
    require(!relativePath.startsWith("/")) { "Sync path must be relative: $relativePath" }
    val root = normalizeRemotePath(hostDirectoryPath)
    val joined = normalizeRemotePath("$root/$relativePath")
    require(joined.isWithin(root)) { "Sync path escapes its root: $relativePath" }
    return joined
}

/** Uploads the local edits to an SSH host before a command regenerates project metadata there.
 *  Local and WSL toolkits read the same files the IDE wrote, so they need no upload. */
suspend fun syncBeforeFetch(
    project: Project,
    toolkit: Toolkit,
    hostDirectoryPath: String,
    localDirectoryPath: String,
) {
    if (toolkit.host.type != ToolkitHostType.SSH) return
    withBackgroundProgress(project, "Sync directory", cancellable = true) {
        transferProjectFiles(
            project,
            toolkit,
            SyncDirection.LOCAL_TO_REMOTE,
            hostDirectoryPath,
            localDirectoryPath,
        )
    }
}

/** Fetches one generated file to a temporary sibling, validates it, and only then replaces
 *  the local file; an invalid or failed fetch keeps the existing file. Local and WSL toolkits
 *  write in place, so for them the fetch only refreshes the virtual file system. */
suspend fun fetchGeneratedFile(
    project: Project,
    toolkit: Toolkit,
    hostDirectoryPath: String,
    localDirectoryPath: String,
    generatedFileRelativePath: String,
) {
    if (toolkit.host.type != ToolkitHostType.SSH) {
        refreshVirtualFileSystem(localDirectoryPath)
        return
    }
    withBackgroundProgress(project, "Sync directory", cancellable = true) {
        val tempRelativePath = "$generatedFileRelativePath.tmp"
        try {
            // The remote file keeps its real name; the temp suffix is local only.
            ToolkitHostExtension.requireForHostType(ToolkitHostType.SSH).syncProject(
                toolkit.host,
                SyncDirection.REMOTE_TO_LOCAL,
                resolveSshSyncPath(hostDirectoryPath, generatedFileRelativePath),
                Paths.get(localDirectoryPath, tempRelativePath).toString(),
            )
            rewriteRemoteRoot(Paths.get(localDirectoryPath, tempRelativePath), localDirectoryPath)
            replaceFileIfValid(localDirectoryPath, tempRelativePath, generatedFileRelativePath)
        } finally {
            Files.deleteIfExists(Paths.get(localDirectoryPath, tempRelativePath))
            refreshVirtualFileSystem(localDirectoryPath)
        }
    }
}

private fun rewriteRemoteRoot(tempFile: NioPath, localDirectoryPath: String) {
    val text = Files.readString(tempFile)
    val remoteRoot = DIRECTORY_FIELD_PATTERN.find(text)?.groupValues?.get(1)
        ?.takeIf { it.length > 1 && it.startsWith("/") }
        ?: return
    val localRoot = localDirectoryPath.replace('\\', '/')
    // The delimiter lookahead keeps a sibling path sharing the prefix (/root vs /root2) intact.
    val rewritten = Regex("${Regex.escape(remoteRoot)}(?=[\"/])").replace(text) { localRoot }
    if (rewritten != text) Files.writeString(tempFile, rewritten)
}

private val DIRECTORY_FIELD_PATTERN = Regex("\"directory\"\\s*:\\s*\"([^\"]*)\"")

/** Replaces the destination file only when the fetched candidate is a non-empty JSON array. */
private fun replaceFileIfValid(localDirectoryPath: String, tempRelativePath: String, destinationRelativePath: String) {
    val tempFile = Paths.get(localDirectoryPath, tempRelativePath)
    val element = try {
        Json.parseToJsonElement(Files.readString(tempFile))
    } catch (_: SerializationException) {
        throw IllegalStateException("The fetched $destinationRelativePath is not valid JSON. The existing file is kept.")
    } catch (_: NoSuchFileException) {
        throw IllegalStateException("The fetched $destinationRelativePath is missing. The existing file is kept.")
    } catch (error: IOException) {
        throw IllegalStateException("The fetched $destinationRelativePath is unreadable. The existing file is kept.", error)
    }
    if (element !is JsonArray || element.isEmpty()) {
        throw IllegalStateException("The fetched $destinationRelativePath is empty. The existing file is kept.")
    }
    val destinationFile = Paths.get(localDirectoryPath, destinationRelativePath)
    Files.createDirectories(destinationFile.parent)
    try {
        Files.move(tempFile, destinationFile, REPLACE_EXISTING, ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(tempFile, destinationFile, REPLACE_EXISTING)
    }
}
