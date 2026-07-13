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

import com.intellij.execution.RunManager
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFileManager
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHost
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.run.XMakeRunConfiguration
import io.xmake.utils.extension.ToolkitHostExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

enum class SyncMode {
    SYNC_ONLY,
    FORCE_SYNC,
}

enum class SyncStatus {
    SUCCESS,
    FAILED,
}

enum class SyncDirection { LOCAL_TO_UPSTREAM, UPSTREAM_TO_LOCAL }

fun SyncDirection.toBoolean(): Boolean = when (this) {
    SyncDirection.LOCAL_TO_UPSTREAM -> false
    SyncDirection.UPSTREAM_TO_LOCAL -> true
}

fun syncProjectByWslSync(
    project: Project,
    host: ToolkitHost,
    direction: SyncDirection,
    directoryPath: String,
    relativePath: String? = null,
) {
    val wslDistribution = host.target as? WSLDistribution ?: throw IllegalArgumentException()

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(
        object : Task.Backgroundable(project, "Sync directory", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Syncing WSL files"

                val localRoot = project.guessProjectDir()?.toNioPath()
                    ?: project.basePath?.let { Path(it) }
                    ?: throw IllegalStateException("Cannot resolve project directory")
                val upstreamRoot = Path(wslDistribution.toWindowsPath(directoryPath))
                val syncPaths = resolveSyncPaths(localRoot, upstreamRoot, relativePath)

                runSyncWithVfsRefresh(syncAction = {
                    when (direction) {
                        SyncDirection.LOCAL_TO_UPSTREAM -> copyPath(syncPaths.local, syncPaths.upstream, indicator)
                        SyncDirection.UPSTREAM_TO_LOCAL -> copyPath(syncPaths.upstream, syncPaths.local, indicator)
                    }
                })
            }

            override fun onCancel() {}

            override fun onFinished() {}
        },
        ProgressIndicatorBase()
    )
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

internal fun runSyncWithVfsRefresh(
    syncAction: () -> Unit,
    refreshAction: () -> Unit = ::refreshVirtualFileSystem,
) {
    try {
        syncAction()
    } finally {
        refreshAction()
    }
}

private fun refreshVirtualFileSystem() {
    invokeLater {
        runWriteAction {
            VirtualFileManager.getInstance().syncRefresh()
        }
    }
}

internal fun copyPath(source: NioPath, target: NioPath, indicator: ProgressIndicator) {
    throwIfCanceled(indicator)

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
        copyDirectoryContents(resolvedSource, targetPath, indicator)
    } else {
        copyFile(resolvedSource, targetPath, indicator)
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

private fun copyDirectoryContents(sourceRoot: NioPath, targetRoot: NioPath, indicator: ProgressIndicator) {
    Files.createDirectories(targetRoot)
    Files.walkFileTree(sourceRoot, object : SimpleFileVisitor<NioPath>() {
        override fun preVisitDirectory(
            dir: NioPath,
            attrs: BasicFileAttributes,
        ): FileVisitResult {
            throwIfCanceled(indicator)
            val target = targetRoot.resolve(sourceRoot.relativize(dir).toString())
            Files.createDirectories(target)
            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: NioPath, attrs: BasicFileAttributes): FileVisitResult {
            throwIfCanceled(indicator)
            val target = targetRoot.resolve(sourceRoot.relativize(file).toString())
            copyFile(file, target, indicator)
            return FileVisitResult.CONTINUE
        }
    })
}

private fun throwIfCanceled(indicator: ProgressIndicator) {
    if (indicator.isCanceled) {
        throw ProcessCanceledException()
    }
}

private fun copyFile(source: NioPath, target: NioPath, indicator: ProgressIndicator) {
    indicator.text2 = source.fileName?.toString() ?: source.toString()
    target.parent?.let { Files.createDirectories(it) }

    try {
        Files.copy(source, target, REPLACE_EXISTING, COPY_ATTRIBUTES)
    } catch (_: UnsupportedOperationException) {
        Files.copy(source, target, REPLACE_EXISTING)
    }
}

private val scope = CoroutineScope(Dispatchers.IO)

fun transferFolderByToolkit(
    project: Project,
    toolkit: Toolkit,
    direction: SyncDirection,
    directoryPath: String = (RunManager.getInstance(project).selectedConfiguration?.configuration as XMakeRunConfiguration).runWorkingDir,
    relativePath: String? = null,
) {

    when (toolkit.host.type) {
        ToolkitHostType.LOCAL -> {
            refreshVirtualFileSystem()
        }
        ToolkitHostType.WSL -> {
            syncProjectByWslSync(
                project,
                toolkit.host,
                direction,
                directoryPath,
                relativePath
            )
        }
        ToolkitHostType.SSH -> {
            val path = relativePath?.let { Path(directoryPath).resolve(relativePath).toCanonicalPath() }
                ?: directoryPath
            EP_NAME.extensions.first { it.KEY == "SSH" }
                .syncProject(scope, project, toolkit.host, direction, path)
        }
    }
}

fun syncBeforeFetch(project: Project, toolkit: Toolkit) {
    transferFolderByToolkit(project, toolkit, SyncDirection.LOCAL_TO_UPSTREAM, relativePath = null)
}

fun fetchGeneratedFile(project: Project, toolkit: Toolkit, fileRelatedPath: String) {
    transferFolderByToolkit(project, toolkit, SyncDirection.UPSTREAM_TO_LOCAL, relativePath = fileRelatedPath)
}
