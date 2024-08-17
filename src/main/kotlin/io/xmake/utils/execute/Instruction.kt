package io.xmake.utils.execute

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.containers.map2Array

val predefinedPath = mapOf(
    "windows" to arrayOf(),
    "unix" to arrayOf(
        // Todo: Add more paths
        "\${HOME}/.local/bin",
        "/usr/local/bin",
        "/usr/bin",
        "/opt/homebrew/bin"
    )
)

val probeEnvCommand = GeneralCommandLine("uname")
    .withParameters("-a")
    .withCharset(Charsets.UTF_8)

val probeXmakeLocCommandOnWin = GeneralCommandLine("where.exe")
    .withParameters("xmake")
    .withCharset(Charsets.UTF_8)

val probeXmakeLocCommand = GeneralCommandLine("which")
    .withParameters(*arrayOf("xmake")
        .plus(predefinedPath["unix"]?.map2Array { "$it/xmake" } ?: emptyArray())
    )
    .withCharset(Charsets.UTF_8)

val probeXmakeVersionCommand = GeneralCommandLine()
    .withParameters("--version")
    .withCharset(Charsets.UTF_8)

val probeXmakeTargetCommand = GeneralCommandLine()
    .withParameters("l")
    .withParameters("-c")
    .withParameters("""
        import('core.project.config'); 
        import('core.project.project'); 
        config.load(); 
        for name, _ in pairs((project.targets())) 
            do print(name) 
        end
    """.trimIndent())

