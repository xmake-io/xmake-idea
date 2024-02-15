package io.xmake.utils.interact

import io.xmake.utils.SystemUtils
import io.xmake.utils.ioRunv

val kXMakeFind:Boolean
    get() = TODO()

val kXMakeVersion:String
    get() {
        val ioTemp: String = ioRunv(listOf(SystemUtils.xmakeProgram, "--version"))[0]
        if (ioTemp.isNotEmpty()) {
            val verTemp = ioTemp.split(' ')[1]
            return verTemp.substring(1, verTemp.length - 1)

        }
        return ioTemp
    }

val kXMakeInstallDir:String
    get() = TODO()

val kArchList:List<String>
    get() = TODO()

val kPlatList:List<String>
    get() = TODO()

val kPlatArchMap:Map<String, List<String>>
    get() = TODO()