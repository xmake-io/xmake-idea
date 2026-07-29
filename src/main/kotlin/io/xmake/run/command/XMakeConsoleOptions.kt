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
package io.xmake.run.command

/**
 * Controls how one XMake process is presented to its output consumer.
 *
 * Command execution owns these flags instead of the action that started it.
 * This keeps Build, Clean, Run preparation, and debug preparation consistent
 * when they share the same process handler.
 *
 * Showing the console is independent from problem parsing and exit-code
 * decoration: a background preparation command can report diagnostics
 * without stealing focus from the platform run content.
 *
 * The options are attached to the process handler at creation time. They do
 * not alter the command line or the selected run configuration, and they are
 * intentionally immutable for the lifetime of that process.
 */
internal data class XMakeConsoleOptions(
    val showConsole: Boolean = true,
    val showProblems: Boolean = false,
    val showExitCode: Boolean = false,
    /** Leaves ANSI escapes for a downstream console that performs its own decoding. */
    val preserveAnsiEscapes: Boolean = false,
)
