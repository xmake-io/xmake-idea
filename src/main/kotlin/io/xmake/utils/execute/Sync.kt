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
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFileManager
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
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path as NioPath
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.Path

private val EP_NAME: ExtensionPointName<ToolkitHostExtension> = ExtensionPointName("io.xmake.toolkitHostExtension")

enum class SyncDirection { LOCAL_TO_UPSTREAM, UPSTREAM_TO_LOCAL }

private suspend fun transferWslFolder(
    project: Project,
    host: ToolkitHost,
    direction: SyncDirection,
    directoryPath: String,
    relativePath: String? = null,
) {
    val wslDistribution = host.target as? WSLDistribution ?: throw IllegalArgumentException()
    val cancellationContext = currentCoroutineContext()
    runInterruptible(Dispatchers.IO) {
        val localRoot = project.guessProjectDir()?.toNioPath()
            ?: project.basePath?.let { Path(it) }
            ?: throw IllegalStateException("Cannot resolve project directory")
        val upstreamRoot = Path(wslDistribution.toWindowsPath(directoryPath))
        val syncPaths = resolveSyncPaths(localRoot, upstreamRoot, relativePath)
        val checkCanceled = { cancellationContext.ensureActive() }

        when (direction) {
            SyncDirection.LOCAL_TO_UPSTREAM -> copyPath(syncPaths.local, syncPaths.upstream, checkCanceled)
            SyncDirection.UPSTREAM_TO_LOCAL -> copyPath(syncPaths.upstream, syncPaths.local, checkCanceled)
        }
    }
}

private fun WSLDistribution.toWindowsPath(path: String): String {
    return if (path.startsWith("/")) getWindowsPath(path) else path
}

internal data class SyncPaths(val local: NioPath, val upstream: NioPath)

internal fun resolveSyncPaths(
    localRoot: NioPath,
    upstreamRoot: NioPath,
    relativePath: String?
): SyncPaths {
    val normalizedLocalRoot = localRoot.normalize()
    val normalizedUpstreamRoot = upstreamRoot.normalize()
    val relativeValue = relativePath?.takeIf { it.isNotBlank() }
    require(relativeValue?.firstOrNull() !in setOf('/', '\\')) {
        "Sync path must be relative: $relativePath"
    }
    val relative = relativeValue
        ?.let(::Path)
        ?.normalize()

    require(relative?.isAbsolute != true) { "Sync path must be relative: $relativePath" }

    val local = relative?.let(normalizedLocalRoot::resolve)?.normalize() ?: normalizedLocalRoot
    val upstream = relative?.let(normalizedUpstreamRoot::resolve)?.normalize() ?: normalizedUpstreamRoot
    require(local.startsWith(normalizedLocalRoot) && upstream.startsWith(normalizedUpstreamRoot)) {
        "Sync path escapes its root: $relativePath"
    }
    return SyncPaths(local, upstream)
}

private suspend fun refreshVirtualFileSystem() {
    withContext(NonCancellable + Dispatchers.EDT) {
        runWriteAction {
            VirtualFileManager.getInstance().syncRefresh()
        }
    }
}

internal fun copyPath(source: NioPath, target: NioPath, checkCanceled: () -> Unit) {
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
        copyDirectoryContents(resolvedSource, targetPath, checkCanceled)
    } else {
        copyFile(resolvedSource, targetPath)
    }
}

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

private fun copyDirectoryContents(sourceRoot: NioPath, targetRoot: NioPath, checkCanceled: () -> Unit) {
    Files.createDirectories(targetRoot)
    Files.walkFileTree(sourceRoot, object : SimpleFileVisitor<NioPath>() {
        override fun preVisitDirectory(
            dir: NioPath,
            attrs: BasicFileAttributes,
        ): FileVisitResult {
            checkCanceled()
            val target = targetRoot.resolve(sourceRoot.relativize(dir).toString())
            Files.createDirectories(target)
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: NioPath, attrs: BasicFileAttributes): FileVisitResult {
            checkCanceled()
            val target = targetRoot.resolve(sourceRoot.relativize(file).toString())
            copyFile(file, target)
            return FileVisitResult.CONTINUE
        }
    })
}

private fun copyFile(source: NioPath, target: NioPath) {
    ProgressManager.progress2(source.fileName?.toString() ?: source.toString())
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
    directoryPath: String,
    relativePath: String? = null,
) {
    if (project.isDisposed) {
        throw ProcessCanceledException()
    }
    require(directoryPath.isNotBlank()) { "Sync directory must be explicit" }

    if (toolkit.host.type == ToolkitHostType.LOCAL) {
        refreshVirtualFileSystem()
        return
    }

    try {
        when (toolkit.host.type) {
            ToolkitHostType.LOCAL -> Unit
            ToolkitHostType.WSL -> transferWslFolder(
                project,
                toolkit.host,
                direction,
                directoryPath,
                relativePath,
            )
            ToolkitHostType.SSH -> {
                val path = resolveSshSyncPath(directoryPath, relativePath)
                EP_NAME.extensions.first { it.KEY == "SSH" }
                    .syncProject(project, toolkit.host, direction, path)
            }
        }
    } finally {
        refreshVirtualFileSystem()
    }
}

internal fun resolveSshSyncPath(directoryPath: String, relativePath: String?): String {
    require(directoryPath.isNotBlank()) { "Sync directory must be explicit" }
    return relativePath?.let { Path(directoryPath).resolve(it).toCanonicalPath() } ?: directoryPath
}

suspend fun syncBeforeFetch(project: Project, toolkit: Toolkit, directoryPath: String) {
    withBackgroundProgress(project, "Sync directory", cancellable = true) {
        transferProjectFiles(
            project,
            toolkit,
            SyncDirection.LOCAL_TO_UPSTREAM,
            directoryPath,
        )
    }
}

suspend fun fetchGeneratedFile(
    project: Project,
    toolkit: Toolkit,
    directoryPath: String,
    fileRelatedPath: String,
) {
    withBackgroundProgress(project, "Sync directory", cancellable = true) {
        transferProjectFiles(
            project,
            toolkit,
            SyncDirection.UPSTREAM_TO_LOCAL,
            directoryPath,
            fileRelatedPath,
        )
    }
}
