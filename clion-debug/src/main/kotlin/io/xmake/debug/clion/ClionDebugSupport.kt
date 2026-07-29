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
package io.xmake.debug.clion

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.platform.dap.DapProcessStarter
import com.intellij.platform.dap.DapStartRequest
import com.intellij.xdebugger.XDebugProcessStarter
import io.xmake.debug.XMakeDebugLaunch
import io.xmake.debug.XMakeDebugSupport
import io.xmake.debug.clion.dap.XMakeDapLaunchArguments
import io.xmake.debug.clion.dap.XMakeDapLaunchState
import io.xmake.debug.clion.dap.XMakeDebugAdapterId
import io.xmake.debug.clion.utils.Logger

/** Connects resolved XMake launches to IntelliJ Platform's public DAP lifecycle. */
class ClionDebugSupport : XMakeDebugSupport {

    override fun createProcessStarter(
        launch: XMakeDebugLaunch,
        environment: ExecutionEnvironment,
    ): XDebugProcessStarter {
        Logger.i(
            TAG,
            "Creating DAP process starter: project=${environment.project.name}, " +
                "driver=${launch.driver.displayName}, target=${launch.executablePath}",
        )
        return DapProcessStarter(
            environment,
            environment.executor,
            XMakeDapLaunchState(launch),
            XMakeDebugAdapterId,
            DapStartRequest.Launch,
            XMakeDapLaunchArguments.create(launch, environment.project),
        )
    }

    private companion object {
        const val TAG = "ClionDebugSupport"
    }
}
