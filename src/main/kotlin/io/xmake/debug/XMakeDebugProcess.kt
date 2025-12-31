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
    
    override fun start() {
        super.start()
    }
    
    override fun doLoadTarget(driver: DebuggerDriver): DebuggerDriver.Inferior {
        return super.doLoadTarget(driver)
    }
}
