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

import com.intellij.build.process.BuildProcessHandler
import kotlinx.coroutines.Job
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Exposes coroutine-backed XMake execution to the Build tool window. */
internal class XMakeBuildProcessHandler(
    private val executionName: String,
) : BuildProcessHandler() {
    private val cancellationRequested = AtomicBoolean()
    private val executionJob = AtomicReference<Job?>()

    override fun getExecutionName(): String = executionName

    fun bind(job: Job) {
        check(executionJob.compareAndSet(null, job))
        if (cancellationRequested.get()) job.cancel()
    }

    fun finish(exitCode: Int) {
        if (!isProcessTerminated) notifyProcessTerminated(exitCode)
    }

    override fun destroyProcessImpl() {
        cancellationRequested.set(true)
        executionJob.get()?.cancel()
    }

    override fun detachProcessImpl() {
        notifyProcessDetached()
    }

    override fun detachIsDefault(): Boolean = false

    override fun getProcessInput(): OutputStream? = null
}
