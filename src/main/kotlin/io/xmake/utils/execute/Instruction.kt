package io.xmake.utils.execute

import com.intellij.execution.configurations.GeneralCommandLine

val probeEnvCommand = GeneralCommandLine("uname")
    .withParameters("-a")
    .withCharset(Charsets.UTF_8)

val probeXmakeLocCommandOnWin = GeneralCommandLine("where.exe")
    .withParameters("xmake")
    .withCharset(Charsets.UTF_8)

val probeXmakeLocCommand = GeneralCommandLine("which")
    .withParameters("xmake")
    .withCharset(Charsets.UTF_8)

val probeXmakeVersionCommand = GeneralCommandLine("xmake")
    .withParameters("--version")
    .withCharset(Charsets.UTF_8)

val probeXmakeTargetCommand = GeneralCommandLine("xmake")
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
