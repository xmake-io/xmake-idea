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
package io.xmake.build

import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.task.ProjectTaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal suspend fun runXMakeBuildTask(project: Project, task: XMakeBuildTask) {
    if (project.isDisposed) {
        throw ExecutionException("Project was disposed before XMake could start")
    }
    val result = withContext(Dispatchers.EDT) {
        FileDocumentManager.getInstance().saveAllDocuments()
        suspendCancellableCoroutine { continuation ->
            val promise = ProjectTaskManager.getInstance(project).run(task)
            promise.onSuccess { result -> continuation.resumeWith(Result.success(result)) }
            promise.onError { error -> continuation.resumeWith(Result.failure(error)) }
        }
    }
    if (result.isAborted || result.hasErrors()) {
        throw ExecutionException("${task.getPresentableName()} failed")
    }
}
