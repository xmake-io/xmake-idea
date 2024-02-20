package io.xmake.utils.interact


val kSysEnv: MutableMap<String, String>?
    get() = System.getenv()

val kLineSep: String
    get() = System.lineSeparator()

val kSysLang: String
    get() = System.getProperty("user.language")

val kSysThreadCount: Int
    get() = Runtime.getRuntime().availableProcessors()