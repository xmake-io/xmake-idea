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
 * @file        ToolkitHostExtension.kt
 *
 */
package io.xmake.utils.extension

import com.intellij.openapi.extensions.ExtensionPointName
import io.xmake.project.directory.ui.ToolkitHostBrowser
import io.xmake.project.toolkit.ToolkitHostType
import io.xmake.project.toolkit.ToolkitHostProvider

interface ToolkitHostExtension : ToolkitHostProvider, ToolkitHostCapabilities, ToolkitHostBrowser {

    companion object {
        private val EP_NAME: ExtensionPointName<ToolkitHostExtension> =
            ExtensionPointName.create("io.xmake.toolkitHostExtension")

        fun forHostType(hostType: ToolkitHostType): ToolkitHostExtension? =
            EP_NAME.extensionList.firstOrNull { extension -> extension.hostType == hostType }

        fun requireForHostType(hostType: ToolkitHostType): ToolkitHostExtension =
            forHostType(hostType) ?: error("$hostType toolkit host extension is unavailable")
    }
}
