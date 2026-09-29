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
 * @file        Sync.kt
 *
 */
package io.xmake.utils.execute

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.ide.progress.withBackgroundProgress
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.utils.extension.ToolkitHostExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path as NioPath
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.Path

enum class SyncDirection { LOCAL_TO_REMOTE, REMOTE_TO_LOCAL }

internal val defaultSyncExcludedEntryNames = setOf(".xmake", ".idea", "build", ".git", ".gitignore")

/** Whether [path] takes part in syncing; the default scope excludes build and VCS artifacts. */
internal fun isDefaultSyncIncluded(path: NioPath): Boolean =
    path.fileName?.toString() !in defaultSyncExcludedEntryNames

private suspend fun transferWslFolder(
    host: ToolkitHost,
    direction: SyncDirection,
    localDirectoryPath: String,
    hostDirectoryPath: String,
    relativePath: String? = null,
) {
    val wslDistribution = host.wslDistribution
        ?: throw IllegalArgumentException("XMake WSL host backend is not available")
    val cancellationContext = currentCoroutineContext()
    val reportProgress: (NioPath) -> Unit = { path ->
        ProgressManager.progress2(path.fileName?.toString() ?: path.toString())
    }
    runInterruptible(Dispatchers.IO) {
        val localRoot = Path(localDirectoryPath)
        val remoteRoot = Path(wslDistribution.toWindowsPath(hostDirectoryPath))
        val syncPaths = resolveSyncPaths(localRoot, remoteRoot, relativePath)
        val checkCanceled = { cancellationContext.ensureActive() }

        when (direction) {
            SyncDirection.LOCAL_TO_REMOTE -> {
                pruneMissingEntries(syncPaths.local, syncPaths.remote, checkCanceled, ::isDefaultSyncIncluded)
                copyPath(
                    syncPaths.local,
                    syncPaths.remote,
                    checkCanceled,
                    ::isDefaultSyncIncluded,
                    reportProgress,
                )
            }

            // Deliberately unfiltered: the wizard's initial clone must fetch everything the remote
            // xmake create generated, and the SSH backend has no download filter anyway.
            SyncDirection.REMOTE_TO_LOCAL -> copyPath(syncPaths.remote, syncPaths.local, checkCanceled, reportProgress = reportProgress)
        }
    }
}

private fun WSLDistribution.toWindowsPath(path: String): String {
    return if (path.startsWith("/")) getWindowsPath(path) else path
}

internal data class SyncPaths(val local: NioPath, val remote: NioPath)

internal fun resolveSyncPaths(
    localRoot: NioPath,
    remoteRoot: NioPath,
    relativePath: String?
): SyncPaths {
    val normalizedLocalRoot = localRoot.normalize()
    val normalizedRemoteRoot = remoteRoot.normalize()
    val relativeValue = relativePath?.takeIf { it.isNotBlank() }
    require(relativeValue?.firstOrNull() !in setOf('/', '\\')) {
        "Sync path must be relative: $relativePath"
    }
    val relative = relativeValue
        ?.let(::Path)
        ?.normalize()

    require(relative?.isAbsolute != true) { "Sync path must be relative: $relativePath" }

    val local = relative?.let(normalizedLocalRoot::resolve)?.normalize() ?: normalizedLocalRoot
    val remote = relative?.let(normalizedRemoteRoot::resolve)?.normalize() ?: normalizedRemoteRoot
    require(local.startsWith(normalizedLocalRoot) && remote.startsWith(normalizedRemoteRoot)) {
        "Sync path escapes its root: $relativePath"
    }
    return SyncPaths(local, remote)
}

/** Refreshes only the synced local directory instead of the whole VFS, and still completes
 *  after a cancellation so freshly fetched files are immediately visible. */
private suspend fun refreshVirtualFileSystem(localDirectoryPath: String) {
    withContext(NonCancellable + Dispatchers.EDT) {
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(localDirectoryPath))?.let { localDirectory ->
            VfsUtil.markDirtyAndRefresh(false, true, true, localDirectory)
        }
    }
}

internal fun copyPath(
    source: NioPath,
    target: NioPath,
    checkCanceled: () -> Unit,
    shouldTransfer: (NioPath) -> Boolean = { _ -> true },
    reportProgress: (NioPath) -> Unit = {},
) {
    checkCanceled()

    val sourcePath = source.toAbsolutePath().normalize()
    if (!Files.exists(sourcePath)) {
        throw NoSuchFileException(sourcePath.toString())
    }
    val targetPath = target.toResolvedPath()
    val resolvedSource = sourcePath.toRealPath()

    if (resolvedSource == targetPath || Files.exists(targetPath) && Files.isSameFile(resolvedSource, targetPath)) {
        return
    }

    if (Files.isDirectory(resolvedSource) && targetPath.startsWith(resolvedSource)) {
        throw IllegalArgumentException("Sync target cannot be inside the source directory: $targetPath")
    }

    if (Files.isDirectory(resolvedSource)) {
        copyDirectoryContents(resolvedSource, targetPath, checkCanceled, shouldTransfer, reportProgress)
    } else {
        reportProgress(resolvedSource)
        copyFile(resolvedSource, targetPath)
    }
}

/** Resolves symlinks for existing ancestors while preserving the not-yet-created tail. */
private fun NioPath.toResolvedPath(): NioPath {
    val absolutePath = toAbsolutePath().normalize()
    val missingSegments = ArrayDeque<NioPath>()
    var existingAncestor = absolutePath

    while (!Files.exists(existingAncestor)) {
        existingAncestor.fileName?.let(missingSegments::addFirst)
        existingAncestor = existingAncestor.parent ?: return absolutePath
    }

    var resolvedPath = existingAncestor.toRealPath()
    missingSegments.forEach { resolvedPath = resolvedPath.resolve(it.toString()) }
    return resolvedPath.normalize()
}

/** Removes remote entries without a local counterpart so uploads mirror the local project. */
internal fun pruneMissingEntries(
    localRoot: NioPath,
    remoteRoot: NioPath,
    checkCanceled: () -> Unit,
    shouldTransfer: (NioPath) -> Boolean,
) {
    if (!Files.exists(remoteRoot)) return

    Files.walkFileTree(remoteRoot, object : SimpleFileVisitor<NioPath>() {
        override fun preVisitDirectory(dir: NioPath, attrs: BasicFileAttributes): FileVisitResult {
            checkCanceled()
            return if (dir != remoteRoot && !shouldTransfer(dir)) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE
        }

        override fun visitFile(file: NioPath, attrs: BasicFileAttributes): FileVisitResult {
            checkCanceled()
            if (shouldTransfer(file) && !Files.isRegularFile(localRoot.resolve(remoteRoot.relativize(file)))) {
                Files.deleteIfExists(file)
            }
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: NioPath, error: IOException?): FileVisitResult {
            if (dir != remoteRoot && !Files.isDirectory(localRoot.resolve(remoteRoot.relativize(dir)))) {
                runCatching { Files.deleteIfExists(dir) }
            }
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: NioPath, error: IOException): FileVisitResult = FileVisitResult.CONTINUE
    })
}

private fun copyDirectoryContents(
    sourceRoot: NioPath,
    targetRoot: NioPath,
    checkCanceled: () -> Unit,
    shouldTransfer: (NioPath) -> Boolean,
    reportProgress: (NioPath) -> Unit,
) {
    Files.createDirectories(targetRoot)
    val unreadableEntries = mutableListOf<NioPath>()
    Files.walkFileTree(sourceRoot, object : SimpleFileVisitor<NioPath>() {
        override fun preVisitDirectory(
            dir: NioPath,
            attrs: BasicFileAttributes,
        ): FileVisitResult {
            checkCanceled()
            // The source root itself is always transferred, mirroring the prune pass and the SSH
            // backend, so a project directory named e.g. "build" still syncs.
            if (dir != sourceRoot && !shouldTransfer(dir)) return FileVisitResult.SKIP_SUBTREE
            val target = targetRoot.resolve(sourceRoot.relativize(dir).toString())
            Files.createDirectories(target)
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: NioPath, attrs: BasicFileAttributes): FileVisitResult {
            checkCanceled()
            if (!shouldTransfer(file)) return FileVisitResult.CONTINUE
            reportProgress(file)
            val target = targetRoot.resolve(sourceRoot.relativize(file).toString())
            copyFile(file, target)
            return FileVisitResult.CONTINUE
        }

        override fun visitFileFailed(file: NioPath, error: IOException): FileVisitResult {
            unreadableEntries.add(file)
            return FileVisitResult.CONTINUE
        }
    })
    if (unreadableEntries.isNotEmpty()) {
        throw IOException(
            "Sync skipped ${unreadableEntries.size} unreadable entries: " +
                    unreadableEntries.joinToString(limit = 10) { it.fileName?.toString() ?: it.toString() },
        )
    }
}

private fun copyFile(source: NioPath, target: NioPath) {
    target.parent?.let { Files.createDirectories(it) }

    try {
        Files.copy(source, target, REPLACE_EXISTING, COPY_ATTRIBUTES)
    } catch (_: UnsupportedOperationException) {
        Files.copy(source, target, REPLACE_EXISTING)
    }
}

suspend fun transferProjectFiles(
    project: Project,
    toolkit: Toolkit,
    direction: SyncDirection,
    hostDirectoryPath: String,
    localDirectoryPath: String,
    relativePath: String? = null,
) {
    if (project.isDisposed) {
        throw ProcessCanceledException()
    }
    require(hostDirectoryPath.isNotBlank()) { "Sync directory must be explicit" }
    require(localDirectoryPath.isNotBlank()) { "Local sync directory must be explicit" }

    if (toolkit.host.type == ToolkitHostType.LOCAL) {
        refreshVirtualFileSystem(localDirectoryPath)
        return
    }

    try {
        when (toolkit.host.type) {
            ToolkitHostType.LOCAL -> Unit
            ToolkitHostType.WSL -> transferWslFolder(
                toolkit.host,
                direction,
                localDirectoryPath,
                hostDirectoryPath,
                relativePath,
            )

            ToolkitHostType.SSH -> {
                val hostSyncPath = resolveSshSyncPath(hostDirectoryPath, relativePath)
                ToolkitHostExtension.requireForHostType(ToolkitHostType.SSH)
                    .syncProject(toolkit.host, direction, hostSyncPath, localDirectoryPath)
            }
        }
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
    val joined = normalizeRemotePath("$root/${relativePath.trimStart('/')}")
    require(joined.isWithin(root)) { "Sync path escapes its root: $relativePath" }
    return joined
}

suspend fun syncBeforeFetch(
    project: Project,
    toolkit: Toolkit,
    hostDirectoryPath: String,
    localDirectoryPath: String,
) {
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

suspend fun fetchGeneratedFile(
    project: Project,
    toolkit: Toolkit,
    hostDirectoryPath: String,
    localDirectoryPath: String,
    generatedFileRelativePath: String,
) {
    withBackgroundProgress(project, "Sync directory", cancellable = true) {
        transferProjectFiles(
            project,
            toolkit,
            SyncDirection.REMOTE_TO_LOCAL,
            hostDirectoryPath,
            localDirectoryPath,
            generatedFileRelativePath,
        )
    }
}
