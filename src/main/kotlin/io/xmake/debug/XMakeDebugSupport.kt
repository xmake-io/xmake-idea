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
package io.xmake.debug

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.xdebugger.XDebugProcessStarter

/** Supplies IDE-specific debugger integration without coupling the core plugin to CLion. */
interface XMakeDebugSupport {

    fun createProcessStarter(
        launch: XMakeDebugLaunch,
        environment: ExecutionEnvironment,
    ): XDebugProcessStarter

    companion object {
        private val EP_NAME = ExtensionPointName.create<XMakeDebugSupport>("io.xmake.debugSupport")

        internal fun find(): XMakeDebugSupport? = EP_NAME.extensionList.firstOrNull()

        internal fun isAvailable(): Boolean = EP_NAME.extensionList.isNotEmpty()
    }
}
