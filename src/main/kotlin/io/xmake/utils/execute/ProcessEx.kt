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
 * @file        ProcessEx.kt
 *
 */
package io.xmake.utils.execute

import com.intellij.execution.processTools.ExecutionResult
import com.intellij.execution.processTools.getBareExecutionResult
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

/** Thrown when a subprocess does not exit within the allowed time and had to be killed. */
class ProcessTimeoutException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Collects the process's output with a hard time bound. The process is killed on timeout or
 * caller cancellation; killing an already terminated process is a documented no-op, so one
 * cleanup path covers every outcome.
 */
suspend fun Process.awaitBounded(timeout: Duration): ExecutionResult = try {
    withTimeout(timeout) { getBareExecutionResult() }
} catch (error: TimeoutCancellationException) {
    throw ProcessTimeoutException("Process did not exit within $timeout", error)
} finally {
    runCatching { destroyForcibly() }
}
