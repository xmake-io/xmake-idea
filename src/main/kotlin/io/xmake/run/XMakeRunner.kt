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
 * @file        XMakeRunner.kt
 *
 */
package io.xmake.run

import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.RunContentDescriptor
import io.xmake.debug.XMakeDebugSession
import io.xmake.utils.Logger
import com.intellij.openapi.project.Project
import io.xmake.utils.SystemUtils

open class XMakeRunner : XMakeDefaultRunner() {

    override fun canRun(executorId: String, profile: RunProfile): Boolean {
        if (profile !is XMakeRunConfiguration) {
            return false
        }
        
        // Always allow run executor
        if (executorId == DefaultRunExecutor.EXECUTOR_ID) {
            return true
        }
        
        // Only allow debug executor if native debug is available
        if (executorId == DefaultDebugExecutor.EXECUTOR_ID) {
            return SystemUtils.isNativeDebugAvailable()
        }
        
        return false
    }

    override fun getRunnerId(): String = "XMakeRunner"

    override fun doExecute(state: RunProfileState, environment: ExecutionEnvironment): RunContentDescriptor? {
        val configuration = environment.runProfile
        if (configuration !is XMakeRunConfiguration) {
            return null
        }

        if (environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID) {
            // Check if debug is available before starting debug session
            if (!SystemUtils.isNativeDebugAvailable()) {
                Logger.w(TAG, "Debug functionality is not available in this IDE. Please use CLion for C/C++ debugging.")
                return null
            }
            return XMakeDebugSession(state, environment).startDebugSession()
        }

        return super.doExecute(state, environment)
    }
    
    companion object {
        private const val TAG = "XMakeRunner"
    }
}
