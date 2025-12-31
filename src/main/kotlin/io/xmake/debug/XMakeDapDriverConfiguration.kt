package io.xmake.debug

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import org.jetbrains.annotations.NotNull
import java.io.File
import kotlin.collections.mutableMapOf

class XMakeDapDriverConfiguration(
    project: Project,
    private val driverPath: String
) : DapDriverConfiguration(project, getDriverName(driverPath), false, false) {
    
    companion object {
        fun getDriverName(driverPath: String): String {
            val driverInfo = DapDriverDetector.validateDriverPath(driverPath)
            return when (driverInfo?.type) {
                DapDriverDetector.DapDriverType.LLDB_DAP -> "lldb-dap"
                DapDriverDetector.DapDriverType.GDB_DAP -> "gdb-dap"
                else -> "lldb-dap" // fallback
            }
        }
    }

    override fun createDriverCommandLine(@NotNull driver: DebuggerDriver, @NotNull arch: ArchitectureType): GeneralCommandLine {
        val actualDriverPath = if (File(driverPath).exists()) {
            driverPath
        } else {
            // Use the new detector to find best driver
            val bestDriver = DapDriverDetector.findBestDriver()
            bestDriver?.path ?: driverPath
        }
        
        return GeneralCommandLine(actualDriverPath)
            .withWorkDirectory(project.basePath)
            .withEnvironment(EnvironmentUtil.getEnvironmentMap())
    }

    override fun getDapLaunchOptions(commandLine: GeneralCommandLine): Map<String, Any> {
        // Add debug environment variables
        val debugEnv = mutableMapOf<String, String>()
        debugEnv["DYLD_LIBRARY_PATH"] = "/usr/local/opt/llvm/lib"
        debugEnv["LLDB_DEBUGSERVER_PATH"] = "/usr/local/opt/llvm/bin"
        
        return mapOf(
            "program" to commandLine.exePath,
            "cwd" to (commandLine.workDirectory?.path ?: project.basePath ?: ""),
            "env" to debugEnv,
            "stopOnEntry" to false,
            "args" to commandLine.parametersList.list,
            "sourceMap" to mapOf(
                "enabled" to "true"
            ),
            // Enable register and memory viewing
            "showDisassembly" to "auto",
            "enablePrettyPrinting" to true,
            "timeout" to 30000,
            // Memory viewing settings
            "memoryReference" to "hex",
            "displayFormat" to "hex",
            "maxChildren" to 1000,
            "maxArrayLength" to 1000,
            "maxStringLength" to 10000,
            // LLDB initialization commands to enable full debugging features
            "initCommands" to listOf(
                "settings set target.inline-breakpoint-strategy always",
                "settings set target.process.memory-protection-rules false",
                "settings set target.process.memory-cache-line-size 64",
                "settings set target.process.disable-aslr false",
                "settings set target.process.detach-on-error false",
                "settings set target.process.stop-on-sharedlib-events false",
                "settings set target.process.stop-on-sharedlib-load-state false",
                "settings set target.process.stop-on-plugin-load false",
                "settings set target.process.stop-on-dylib-load false",
                "settings set target.process.stop-on-dylib-unload false",
                "settings set target.process.stop-on-objc-throw false",
                "settings set target.process.stop-on-cxx-exception-break false",
                "settings set target.process.stop-on-cxx-exception-throw false",
                "settings set target.process.stop-on-catch-throw false",
                "settings set target.process.stop-on-objc-exception-bp false",
                "settings set target.process.stop-on-cxx-exception-bp false",
                "settings set target.process.stop-on-catch-bp false",
                "settings set target.process.stop-on-exception-bp false",
                "settings set target.process.stop-on-exception-throw false",
                "settings set target.process.stop-on-exception-catch false",
                "settings set target.process.stop-on-exception-rethrow false",
                "settings set target.process.stop-on-exception-terminate false",
                "settings set target.process.stop-on-exception-continue false",
                "settings set target.process.stop-on-exception-exit false",
                "settings set target.process.stop-on-exception-return false",
                "settings set target.process.stop-on-exception-abort false",
                "settings set target.process.stop-on-exception-sigsegv false",
                "settings set target.process.stop-on-exception-sigbus false",
                "settings set target.process.stop-on-exception-sigfpe false",
                "settings set target.process.stop-on-exception-sigill false",
                "settings set target.process.stop-on-exception-sigtrap false",
                "settings set target.process.stop-on-exception-sigabrt false",
                "settings set target.process.stop-on-exception-sigpipe false",
                "settings set target.process.stop-on-exception-sigalrm false",
                "settings set target.process.stop-on-exception-sigterm false",
                "settings set target.process.stop-on-exception-sigkill false",
                "settings set target.process.stop-on-exception-sigint false",
                "settings set target.process.stop-on-exception-sigquit false",
                "settings set target.process.stop-on-exception-sigstop false",
                "settings set target.process.stop-on-exception-sigtstp false",
                "settings set target.process.stop-on-exception-sigcont false",
                "settings set target.process.stop-on-exception-sigchld false",
                "settings set target.process.stop-on-exception-sigttin false",
                "settings set target.process.stop-on-exception-sigttou false",
                "settings set target.process.stop-on-exception-sigurg false",
                "settings set target.process.stop-on-exception-sigxcpu false",
                "settings set target.process.stop-on-exception-sigxfsz false",
                "settings set target.process.stop-on-exception-sigvtalrm false",
                "settings set target.process.stop-on-exception-sigprof false",
                "settings set target.process.stop-on-exception-sigusr1 false",
                "settings set target.process.stop-on-exception-sigusr2 false",
                // Memory viewing specific settings
                "settings set target.memory-module-load-level full",
                "settings set target.memory-module-load-stack-unwind yes",
                "settings set target.memory-module-load-stack-dump yes",
                "settings set target.memory-module-load-dynamic-linker yes",
                "settings set target.memory-module-load-system-libraries yes",
                "settings set target.memory-module-load-user-specified yes",
                "settings set target.memory-module-load-all yes",
                "settings set target.memory-module-load-dependents yes",
                "settings set target.memory-module-load-dependencies yes",
                "settings set target.memory-module-load-reexports yes",
                "settings set target.memory-module-load-local-symbols yes",
                "settings set target.memory-module-load-global-symbols yes",
                "settings set target.memory-module-load-external-symbols yes",
                "settings set target.memory-module-load-debug-symbols yes",
                "settings set target.memory-module-load-type-info yes",
                "settings set target.memory-module-load-objc-info yes",
                "settings set target.memory-module-load-swift-info yes",
                "settings set target.memory-module-load-rust-info yes",
                "settings set target.memory-module-load-go-info yes",
                "settings set target.memory-module-load-dlang-info yes",
                "settings set target.memory-module-load-nim-info yes",
                "settings set target.memory-module-load-zig-info yes",
                "settings set target.memory-module-load-ada-info yes",
                "settings set target.memory-module-load-fortran-info yes",
                "settings set target.memory-module-load-haskell-info yes",
                "settings set target.memory-module-load-ocaml-info yes",
                "settings set target.memory-module-load-scala-info yes",
                "settings set target.memory-module-load-kotlin-info yes",
                "settings set target.memory-module-load-java-info yes",
                "settings set target.memory-module-load-csharp-info yes",
                "settings set target.memory-module-load-vb-info yes",
                "settings set target.memory-module-load-fsharp-info yes",
                "settings set target.memory-module-load-powershell-info yes",
                "settings set target.memory-module-load-python-info yes",
                "settings set target.memory-module-load-ruby-info yes",
                "settings set target.memory-module-load-perl-info yes",
                "settings set target.memory-module-load-php-info yes",
                "settings set target.memory-module-load-javascript-info yes",
                "settings set target.memory-module-load-typescript-info yes",
                "settings set target.memory-module-load-coffeescript-info yes",
                "settings set target.memory-module-load-bash-info yes",
                "settings set target.memory-module-load-zsh-info yes",
                "settings set target.memory-module-load-fish-info yes",
                "settings set target.memory-module-load-csh-info yes",
                "settings set target.memory-module-load-tcsh-info yes",
                "settings set target.memory-module-load-ksh-info yes",
                "settings set target.memory-module-load-sh-info yes"
            )
        )
    }

    override fun getDapAttachOptions(pid: Int): Map<String, Any> {
        return emptyMap()
    }

    override fun createDriver(@NotNull handler: DebuggerDriver.Handler, @NotNull architectureType: ArchitectureType): DapDriver {
        return super.createDriver(handler, architectureType)
    }
}
