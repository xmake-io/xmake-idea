package io.xmake.utils.interact

import io.xmake.utils.SystemUtils
import io.xmake.utils.ioRunv

val kXMakeFind:Boolean
    get() = TODO()

val kXMakeVersion:String
    get() {
        val verTemp: String = ioRunv(listOf(SystemUtils.xmakeProgram, "--version"))[0]
        if (verTemp.isNotEmpty()) {
            return verTemp.split(' ')[1].substring(0, verTemp.length - 1)
        }
        return verTemp
    }

val kXMakeInstallDir:String
    get() = TODO()

val kArchList:List<String>
    get() = TODO()

val kPlatList:List<String>
    get() = TODO()

val kPlatArchMap:Map<String, List<String>>
    get() = TODO()