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
 * @file        XMakeData.kt
 *
 */
package io.xmake.utils.interact

import io.xmake.utils.ioRunvInPool

/*
 * ioTemp: return of ioRunv()
 */

/**
 * [kXMakeFind]
 *
 * @return a boolean, true if xmake is found
 */
val kXMakeFind:Boolean
    get() = ioRunvInPool(listOf("xmake", "--version")).isNotEmpty()

/**
 * [kXMakeVersion]
 *
 * @return string of xmake version
 *
 * like: "2.8.6 HEAD.211710b67"
 *
 * error: return empty string -> ""
 */
val kXMakeVersion:String
    get() {
        val ioTemp: List<String> = ioRunvInPool(listOf("xmake", "--version"))
        if (ioTemp.isNotEmpty()) {
            val verTemp = ioTemp[0].split(' ')[1]
            return verTemp.substring(1, verTemp.length - 1).replace('+', ' ')
        }
        return ""
    }

val kXMakeInstallDir:String
    get() = TODO()

/**
 * [kPlatList]
 *
 * @return a list of plat string
 *
 * error: return empty list -> emptyList()
 */
val kPlatList: List<String>
    get() {
        //TODO("Add: xmake full path as input")
        val ioTemp: List<String> = ioRunvInPool(listOf("xmake", "f", "config"))
        if (ioTemp.isEmpty()) return emptyList()

        val ret: List<String>
        val regex: Regex = Regex("""-\s(\w+)""")
        val searchBegin: String = "-p PLAT"
        val searchEnd: String = "-a ARCH"
        var indexBegin: Int = 0
        var indexEnd: Int = 0

        for (index in ioTemp.indices) {
            if (!ioTemp[index].contains(searchBegin)) {
                continue
            } else {
                indexBegin = index
                break
            }
        }

        for (index in indexBegin until ioTemp.size) {
            if (!ioTemp[index].contains(searchEnd)) {
                continue
            } else {
                indexEnd = index
                break
            }
        }

        val subListTemp = ioTemp.subList(indexBegin + 1, indexEnd)
        ret = subListTemp.map {
            regex.find(it)?.groups?.get(1)?.value ?: ""
        }
        return ret
    }

/**
 * [kPlatArchMap]
 *
 * @return a map of plat and arch list
 *
 * error: return empty map -> emptyMap()
 */
val kPlatArchMap:Map<String, List<String>>
    get() {
        //TODO("Add: xmake full path as input")
        val ioTemp: List<String> = ioRunvInPool(listOf("xmake", "f", "config"))
        if (ioTemp.isEmpty()) return emptyMap()

        val ret: Map<String, List<String>>
        val regex: Regex = Regex("""-\s(\w+): (.+)""")
        val searchBegin: String = "-a ARCH"
        val searchEnd: String = "-m MODE"
        var indexBegin: Int = 0
        var indexEnd: Int = 0

        for (index in ioTemp.indices) {
            if (!ioTemp[index].contains(searchBegin)) {
                continue
            } else {
                indexBegin = index
                break
            }
        }

        for (index in indexBegin until ioTemp.size) {
            if (!ioTemp[index].contains(searchEnd)) {
                continue
            } else {
                indexEnd = index
                break
            }
        }

        val subListTemp: List<String> = ioTemp.subList(indexBegin + 1, indexEnd)
        ret = subListTemp.associate {
            val matchResult = regex.find(it)
            val plat = matchResult?.groups?.get(1)?.value ?: ""
            val arch = matchResult?.groups?.get(2)?.value ?: ""
            plat to arch.split(' ')
        }
        return ret
    }