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
package io.xmake.run

/**
 * Marks a run configuration that runs an xmake target against an XMake build profile, selected
 * through the profile execution targets. Implemented by [XMakeRunConfiguration] and by the
 * CLion-only configuration in the `:clion-run` content module.
 */
interface XMakeProfileRunConfiguration {
    var runTarget: String
    /** Preferred profile reference; the execution target remains the runtime authority. */
    var preferredBuildProfileId: String?
}
