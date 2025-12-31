package io.xmake.debug

import com.intellij.execution.ExecutionException
import com.intellij.execution.filters.TextConsoleBuilder
import com.intellij.xdebugger.XDebugSession
import com.jetbrains.cidr.execution.RunParameters
import com.jetbrains.cidr.execution.debugger.CidrLocalDebugProcess
import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver

class XMakeDebugProcess(
    parameters: RunParameters,
    session: XDebugSession,
    consoleBuilder: TextConsoleBuilder
) : CidrLocalDebugProcess(parameters, session, consoleBuilder) {
    
    init {
        println("XMakeDebugProcess initialized")
    }
    
    override fun start() {
        println("XMakeDebugProcess: start called")
        try {
            println("XMakeDebugProcess: about to call super.start()")
            super.start()
            println("XMakeDebugProcess: start completed successfully")
        } catch (e: Exception) {
            println("XMakeDebugProcess: start failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    override fun doLoadTarget(driver: DebuggerDriver): DebuggerDriver.Inferior {
        println("XMakeDebugProcess: doLoadTarget called")
        try {
            println("XMakeDebugProcess: driver type: ${driver::class.java.simpleName}")
            val inferior = super.doLoadTarget(driver)
            println("XMakeDebugProcess: doLoadTarget completed, inferior: $inferior")
            return inferior
        } catch (e: Exception) {
            println("XMakeDebugProcess: doLoadTarget failed: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
}
