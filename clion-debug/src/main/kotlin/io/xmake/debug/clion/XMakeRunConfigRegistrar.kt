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
 * @file        XMakeRunConfigRegistrar.kt
 *
 */
package io.xmake.debug.clion

import com.intellij.execution.configurations.ConfigurationType
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import io.xmake.debug.clion.utils.Logger

/**
 * Dynamically registers [XMakeExecutableRunConfigurationType] into the (dynamic) `configurationType`
 * extension point. Idempotent. CLion-only; invoked reflectively via
 * [io.xmake.debug.DebugModuleLoader] once CLion is confirmed present.
 */
object XMakeRunConfigRegistrar {

    private const val TAG = "XMakeRunConfigRegistrar"

    @Volatile
    private var registered = false

    /** Whether CLion's external (custom-build-target) run config subsystem is present. */
    @JvmStatic
    fun isAvailable(): Boolean = try {
        Class.forName("com.jetbrains.cidr.cpp.execution.external.run.CLionExternalRunConfiguration")
        true
    } catch (t: Throwable) {
        false
    }

    /**
     * Register the Xmake Executable configuration type, owned by plugin [pluginId]. The type lives for
     * the application lifetime (parent disposable = the application). Idempotent across calls / already
     * statically present types.
     */
    @JvmStatic
    fun register(pluginId: String): Boolean {
        if (registered) return true
        return try {
            val ep = ConfigurationType.CONFIGURATION_TYPE_EP
            if (ep.extensionList.any { it.id == XMakeExecutableRunConfigurationType.ID }) {
                registered = true
                return true
            }
            val descriptor = PluginManagerCore.getPlugin(PluginId.getId(pluginId))
            if (descriptor == null) {
                Logger.e(TAG, "Plugin descriptor not found for id=$pluginId")
                return false
            }
            ep.point.registerExtension(
                XMakeExecutableRunConfigurationType(),
                descriptor,
                ApplicationManager.getApplication()
            )
            registered = true
            Logger.i(TAG, "Registered Xmake Executable run configuration type")
            true
        } catch (t: Throwable) {
            Logger.e(TAG, "Failed to register Xmake Executable run configuration type: ${t.message}")
            t.printStackTrace()
            false
        }
    }
}
