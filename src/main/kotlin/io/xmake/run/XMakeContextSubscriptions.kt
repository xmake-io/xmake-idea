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
 * @file        XMakeContextSubscriptions.kt
 *
 */
package io.xmake.run

import com.intellij.execution.ExecutionTarget
import com.intellij.execution.ExecutionTargetListener
import com.intellij.execution.ExecutionTargetManager
import com.intellij.util.messages.MessageBusConnection
import io.xmake.project.directory.XMakeProjectDirectoryManager
import io.xmake.project.profile.XMakeBuildProfileManager

/** Subscribes [action] to execution target, build profile, and project directory changes. */
internal fun MessageBusConnection.subscribeToContextChanges(action: () -> Unit) {
    val listener = ContextListener(action)
    subscribe(ExecutionTargetManager.TOPIC, listener)
    subscribe(XMakeBuildProfileManager.TOPIC, listener)
    subscribe(XMakeProjectDirectoryManager.TOPIC, listener)
}

private class ContextListener(private val action: () -> Unit) :
    ExecutionTargetListener,
    XMakeBuildProfileManager.Listener,
    XMakeProjectDirectoryManager.Listener {
    override fun activeTargetChanged(newTarget: ExecutionTarget) = action()
    override fun profilesChanged() = action()
    override fun projectDirectoryChanged() = action()
}
