package io.xmake.debug

import com.jetbrains.cidr.execution.debugger.backend.DebuggerDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriver
import com.jetbrains.cidr.execution.debugger.backend.dap.DapDriverConfiguration
import com.jetbrains.cidr.ArchitectureType
import com.jetbrains.cidr.execution.debugger.backend.LLThread
import com.jetbrains.cidr.execution.debugger.backend.LLFrame
import com.jetbrains.cidr.execution.debugger.backend.LLValue
import com.jetbrains.cidr.execution.debugger.backend.LLWatchpoint
import com.intellij.openapi.util.Pair
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.debug.StackTraceArguments
import org.eclipse.lsp4j.debug.ScopesArguments
import java.util.Collections
import java.util.WeakHashMap
import com.intellij.execution.ExecutionException
import com.jetbrains.cidr.execution.Installer
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class XMakeDapDriver(
    private val handler: DebuggerDriver.Handler,
    configuration: DapDriverConfiguration,
    architectureType: ArchitectureType
) : DapDriver(handler, configuration, architectureType) {

    private val frameIds = Collections.synchronizedMap(WeakHashMap<LLFrame, Int>())

    override fun loadForLaunch(installer: Installer, architecture: String?): DebuggerDriver.Inferior {
        return super.loadForLaunch(installer, architecture)
    }

    override fun getFrames(thread: LLThread, start: Int, count: Int, includeInternal: Boolean): DebuggerDriver.ResultList<LLFrame> {
        val args = StackTraceArguments().apply { 
            threadId = thread.id.toInt()
            startFrame = start
            levels = count
        }
        val response = server.stackTrace(args).join()
        val frames = response.stackFrames.map { frame ->
            val llFrame = LLFrame(frame.id.toInt(), frame.name, frame.source?.path, null, frame.line?.toInt() ?: 0, 0, null, true, true, null)
            frameIds[llFrame] = frame.id.toInt()
            llFrame
        }
        return DebuggerDriver.ResultList(frames, (response.totalFrames ?: 0) > (start + count))
    }

    override fun getVariables(threadId: Long, frameIndex: Int): List<LLValue> {
        throw ExecutionException("Deprecated")
    }

    override fun getFrameVariables(thread: LLThread, frame: LLFrame): FrameVariables {
        return FrameVariables(getVariables(thread, frame), true)
    }

    override fun getVariables(thread: LLThread, frame: LLFrame): List<LLValue> {
        val fid = frameIds[frame] ?: 0
        val scopesArgs = ScopesArguments().apply { frameId = fid }
        val scopes = server.scopes(scopesArgs).join().scopes
        
        if (scopes.isNotEmpty()) {
            val varsArgs = org.eclipse.lsp4j.debug.VariablesArguments().apply { variablesReference = scopes[0].variablesReference }
            val vars = server.variables(varsArgs).join().variables
            return vars.map { 
                LLValue(it.name, it.type ?: "unknown", it.value ?: "null", it.variablesReference.toLong(), null as LLValue.TypeClass?, it.name)
            }
        }
        return emptyList()
    }

    override fun removeCodepoints(ids: Collection<Int>) {}
    override fun cancelSymbolsDownload(details: String) {}
    override fun addWatchpoint(threadId: Long, frameIndex: Int, value: LLValue, expr: String, lifetime: LLWatchpoint.Lifetime?, accessType: LLWatchpoint.AccessType): LLWatchpoint { throw ExecutionException("Not supported") }
    override fun removeWatchpoint(ids: List<Int>) {}
    override fun completeConsoleCommand(command: String, pos: Int): DebuggerDriver.ResultList<String> { return DebuggerDriver.ResultList(emptyList(), false) }
}
