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
package io.xmake.project.toolkit

/** The project script's refusal of one toolkit's xmake version. */
internal data class XMakeVersionRefusal(
    val toolkit: Toolkit,
    val minimumVersion: String,
) {
    val message: String
        get() = "The project refused xmake ${toolkit.presentableVersion} on ${toolkit.name} " +
            "because it requires xmake $minimumVersion or newer"
}

/** The project script refused this xmake version. */
internal class XMakeVersionRefusalException(
    val refusal: XMakeVersionRefusal,
    cause: Throwable,
) : RuntimeException(refusal.message, cause)
