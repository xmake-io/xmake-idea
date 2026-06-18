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
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.extensions.ExtensionPointName
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
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.Files
import java.nio.file.Path as NioPath
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlin.io.path.Path

private val Log = fileLogger()

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
                val upstreamRoot = Path(wslDistribution.getWindowsPath(directoryPath))
                val relative = relativePath?.trimStart('/', '\\')?.takeIf { it.isNotBlank() }
                val localPath = relative?.let { localRoot.resolve(it) } ?: localRoot
                val upstreamPath = relative?.let { upstreamRoot.resolve(it) } ?: upstreamRoot

                runSyncWithVfsRefresh(syncAction = {
                    when (direction) {
                        SyncDirection.LOCAL_TO_UPSTREAM -> copyPath(localPath, upstreamPath, indicator)
                        SyncDirection.UPSTREAM_TO_LOCAL -> copyPath(upstreamPath, localPath, indicator)
                    }
                })
            }

            override fun onCancel() {}

            override fun onFinished() {}
        },
        ProgressIndicatorBase()
    )
}

internal fun runSyncWithVfsRefresh(
    syncAction: () -> Unit,
    refreshAction: () -> Unit = ::refreshVirtualFileSystem,
) {
    try {
        syncAction()
    } catch (e: IOException) {
        Log.warn("Failed to sync files", e)
    } catch (e: UncheckedIOException) {
        Log.warn("Failed to sync files", e)
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

private fun copyPath(source: NioPath, target: NioPath, indicator: ProgressIndicator) {
    if (!Files.exists(source)) {
        Log.warn("Skipping sync because source path does not exist: $source")
        return
    }

    if (Files.isDirectory(source)) {
        copyDirectoryContents(source, target, indicator)
    } else {
        copyFile(source, target, indicator)
    }
}

private fun copyDirectoryContents(sourceRoot: NioPath, targetRoot: NioPath, indicator: ProgressIndicator) {
    Files.createDirectories(targetRoot)
    Files.walk(sourceRoot).use { paths ->
        paths.forEach { source ->
            if (indicator.isCanceled) {
                return@forEach
            }

            val target = targetRoot.resolve(sourceRoot.relativize(source).toString())
            if (Files.isDirectory(source)) {
                Files.createDirectories(target)
            } else {
                copyFile(source, target, indicator)
            }
        }
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