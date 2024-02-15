package io.xmake.utils.interact


val kSystemEnv: MutableMap<String, String>?
    get() = System.getenv()

val kLineSeparator: String
    get() = System.lineSeparator()