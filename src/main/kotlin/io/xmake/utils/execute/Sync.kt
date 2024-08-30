package io.xmake.utils.execute

import com.intellij.execution.RunManager
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.TargetProgressIndicatorAdapter
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.target.WslTargetEnvironment
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration
import com.intellij.execution.wsl.target.WslTargetEnvironmentRequest
import com.intellij.openapi.application.EDT
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
import kotlinx.coroutines.withContext
import kotlin.io.path.Path
import kotlin.io.path.isDirectory

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
    scope: CoroutineScope,
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
                scope.launch {
                    indicator.isIndeterminate = true

                    /*                    for (i in 1..100) {
                                            if (indicator.isCanceled) {
                                                break
                                            }
                                            withContext(Dispatchers.EDT) {
                                                indicator.fraction = i / 100.0
                                                indicator.text = "Processing $i%"
                                            }
                                        }*/

                    val wslTargetEnvironmentRequest = WslTargetEnvironmentRequest(
                        WslTargetEnvironmentConfiguration(wslDistribution)
                    ).apply {
                        downloadVolumes.add(
                            TargetEnvironment.DownloadRoot(
                                project.guessProjectDir()!!.toNioPath(),
                                TargetEnvironment.TargetPath.Persistent(directoryPath)
                            )
                        )
                        uploadVolumes.add(
                            TargetEnvironment.UploadRoot(
                                project.guessProjectDir()!!.toNioPath(),
                                TargetEnvironment.TargetPath.Persistent(directoryPath),
                            ).also { println(it.targetRootPath) }.apply {
                                this.volumeData
                            }
                        )
                        shouldCopyVolumes = true
                    }

                    val wslTargetEnvironment = WslTargetEnvironment(
                        wslTargetEnvironmentRequest,
                        wslDistribution
                    )

                    when (direction) {
                        SyncDirection.LOCAL_TO_UPSTREAM -> {
                            wslTargetEnvironment.uploadVolumes.forEach { root, volume ->
                                println("upload: ${root.localRootPath}, ${root.targetRootPath}")
                                volume.upload(relativePath ?: "", TargetProgressIndicatorAdapter(indicator))
                            }
                        }

                        SyncDirection.UPSTREAM_TO_LOCAL -> {
                            wslTargetEnvironment.downloadVolumes.forEach { root, volume ->
                                volume.download(relativePath ?: "", indicator)
                            }
                        }
                    }

                    withContext(Dispatchers.EDT) {
                        runWriteAction {
                            VirtualFileManager.getInstance().syncRefresh()
                        }
                    }

                }
            }

            override fun onCancel() {}

            override fun onFinished() {}
        },
        ProgressIndicatorBase()
    )
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
            invokeLater {
                runWriteAction {
                    VirtualFileManager.getInstance().syncRefresh()
                }
            }
        }
        ToolkitHostType.WSL -> {
            syncProjectByWslSync(
                scope,
                project,
                toolkit.host,
                direction,
                directoryPath,
                relativePath?.let { if (Path(it).isDirectory()) relativePath else null }
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