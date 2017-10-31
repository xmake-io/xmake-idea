package org.tboox.xmake.utils

import com.intellij.openapi.util.SystemInfo

object SystemUtils {

    // get platform
    fun platform(): String = when {
        SystemInfo.isWindows -> "windows"
        SystemInfo.isMac -> "macosx"
        else -> "linux"
    }
}
