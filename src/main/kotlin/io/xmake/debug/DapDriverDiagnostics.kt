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
 * @file        DapDriverDiagnostics.kt
 *
 */
package io.xmake.debug

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import io.xmake.utils.execute.runProcessBlocking

object DapDriverDiagnostics {

    @Synchronized
    fun diagnoseWindowsDllFailure(commandLine: GeneralCommandLine): Boolean {
        if (!SystemInfo.isWindows) return false
        val identity = DriverIdentity.of(commandLine.exePath)
        checkedDrivers[identity]?.let { return it }

        val probe = GeneralCommandLine(commandLine.exePath)
            .withWorkDirectory(commandLine.workDirectory)
            .withEnvironment(commandLine.environment)
            .withParameters("--version")
        val failure = try {
            val output = probe.runProcessBlocking(DRIVER_CHECK_TIMEOUT)
            if (output.isTimeout) {
                Log.warn("Timed out while checking ${commandLine.exePath}")
                return false
            }
            output.exitCode == STATUS_DLL_NOT_FOUND.toInt()
        } catch (error: Exception) {
            Log.warn("Failed to check ${commandLine.exePath}: ${error.message}")
            return false
        }
        checkedDrivers[identity] = failure
        return failure
    }

    private val checkedDrivers = mutableMapOf<DriverIdentity, Boolean>()

    /** Windows exit code STATUS_DLL_NOT_FOUND (0xC0000135): the driver failed to load a required DLL. */
    private const val STATUS_DLL_NOT_FOUND = 0xC0000135L

    private val Log = logger<DapDriverDiagnostics>()
}
