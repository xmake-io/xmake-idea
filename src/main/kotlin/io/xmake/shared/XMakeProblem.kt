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
 * @file        XMakeProblem.kt
 *
 */
package io.xmake.shared

import java.nio.file.InvalidPathException
import java.nio.file.Path

class XMakeProblem(
    val file: String? = null,
    val line: String? = "0",
    val column: String? = "0",
    val kind: String? = "error",
    val message: String? = "",
    val workingDirectory: Path? = null,
) {
    internal val resolvedFilePath: Path?
        get() {
            val filePath = file?.takeIf(String::isNotBlank) ?: return null
            val path = try {
                Path.of(filePath)
            } catch (_: InvalidPathException) {
                return null
            }

            return if (path.isAbsolute) {
                path.normalize()
            } else {
                workingDirectory?.resolve(path)?.normalize()
            }
        }
}
