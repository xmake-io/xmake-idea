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
 * @file        ActivatedToolkit.kt
 *
 */
package io.xmake.project.toolkit

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import io.xmake.project.xmakeSettings
import io.xmake.run.XMakeRunConfiguration

val Project.activatedToolkit: Toolkit?
    get() = RunManager.getInstance(this).selectedConfiguration?.configuration.let {
        if (it is XMakeRunConfiguration) it.runToolkit else null
    }

/**
 * The project's active xmake toolkit, independent of which run configuration is selected. Resolves
 * (in order) the toolkit pinned in [io.xmake.project.XMakeSettings] by id, then the selected run
 * config's toolkit ([activatedToolkit]), then the first registered toolkit. This is what config and
 * command building should use in the native flow, where [activatedToolkit] is null.
 */
val Project.xmakeActiveToolkit: Toolkit?
    get() {
        val registered = ToolkitManager.getInstance().getRegisteredToolkits()
        val pinnedId = xmakeSettings.state.activeToolkitId
        return registered.firstOrNull { it.id == pinnedId && pinnedId.isNotEmpty() }
            ?: activatedToolkit
            ?: registered.firstOrNull()
    }