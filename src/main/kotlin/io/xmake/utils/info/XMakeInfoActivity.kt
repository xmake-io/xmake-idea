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
 * @file        XMakeInfoActivity.kt
 *
 */
package io.xmake.utils.info

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitChangedNotifier
import io.xmake.project.toolkit.activatedToolkit

import io.xmake.project.toolkit.ToolkitManager

class XMakeInfoActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Initial probe
        val manager = XMakeInfoManager.getInstance(project)
        val toolkit = project.activatedToolkit ?: ToolkitManager.getInstance().getRegisteredToolkits().firstOrNull()
        
        toolkit?.let {
            manager.probeXMakeInfo(it)
            manager.probeXMakeApis(it)
        }

        ApplicationManager.getApplication().messageBus.connect()
            .subscribe(
                ToolkitChangedNotifier.TOOLKIT_CHANGED_TOPIC,
                object : ToolkitChangedNotifier {
                    override fun toolkitChanged(toolkit: Toolkit?) {
                        manager.probeXMakeInfo(toolkit)
                        manager.probeXMakeApis(toolkit)
                    }
                }
            )
    }
}