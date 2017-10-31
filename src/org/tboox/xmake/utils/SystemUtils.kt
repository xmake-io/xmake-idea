package org.tboox.xmake.utils

object SystemUtils {

    // the operation system
    private val OS: String = System.getProperty("os.name").toLowerCase()

    // get platform
    fun platform(): String = when {
        OS.contains("win") -> "windows"
        OS.contains("mac") -> "macosx"
        else -> "linux"
    }
}
