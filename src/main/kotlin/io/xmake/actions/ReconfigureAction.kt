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
 * @file        ReconfigureAction.kt
 *
 */
package io.xmake.actions

import com.intellij.openapi.project.Project
import io.xmake.project.console.XMakeConsole
import io.xmake.shared.XMakeReconfigure

/**
 * Runs `xmake f …` with the active profile + build mode and re-syncs the CLion build targets.
 * Replaces the Reconfigure button of the retired "Xmake Config" tool window.
 */
class ReconfigureAction : XMakeConsoleAction() {

    override fun execute(project: Project, console: XMakeConsole) {
        console.clear()
        XMakeReconfigure.reconfigure(project)
    }
}
