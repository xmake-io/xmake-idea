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
package io.xmake.project.profile

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/** Recent per-profile option snapshots so reopened editors do not re-run XMake queries. */
@Service(Service.Level.PROJECT)
internal class XMakeBuildProfileOptionsCache {
    private val lock = Any()
    private val entries = object : LinkedHashMap<XMakeBuildProfile, XMakeBuildProfileOptions>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<XMakeBuildProfile, XMakeBuildProfileOptions>) =
            size > MAX_ENTRIES
    }

    fun get(profile: XMakeBuildProfile): XMakeBuildProfileOptions? = synchronized(lock) { entries[profile] }

    fun put(profile: XMakeBuildProfile, options: XMakeBuildProfileOptions) {
        synchronized(lock) {
            entries.remove(profile)
            entries[profile] = options
        }
    }

    fun clear() = synchronized(lock) { entries.clear() }

    private companion object {
        const val MAX_ENTRIES = 8
    }
}

internal val Project.xmakeBuildProfileOptionsCache: XMakeBuildProfileOptionsCache
    get() = getService(XMakeBuildProfileOptionsCache::class.java)
        ?: error("Failed to get XMakeBuildProfileOptionsCache for $this")
