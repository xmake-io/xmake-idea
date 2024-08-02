package io.xmake.project.toolkit

import com.intellij.execution.RunManager
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WSLUtil
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.ssh.config.unified.SshConfig
import com.intellij.ssh.config.unified.SshConfigManager
import com.intellij.util.PlatformUtils
import com.intellij.util.xmlb.annotations.XCollection
import io.xmake.project.toolkit.ToolkitHostType.*
import io.xmake.run.XMakeRunConfiguration
import io.xmake.utils.execute.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.*

@Service
@State(name = "toolkits", storages = [Storage("xmakeToolkits.xml")])
class ToolkitManager(private val scope: CoroutineScope) : PersistentStateComponent<ToolkitManager.State> {

    val toolkitSet = mutableSetOf<Toolkit>()
    private lateinit var detectionJob: Job
    private lateinit var validateJob: Job
    private var storage: State = State()
    private val listenerList = mutableListOf<ToolkitDetectedListener>()

    class State{
        @XCollection(propertyElementName = "registeredToolKits")
        val registeredToolkits = mutableSetOf<Toolkit>()
    }

    interface ToolkitDetectedListener : EventListener {
        fun onToolkitDetected(e: ToolkitDetectEvent)
        fun onAllToolkitsDetected()
    }

    class ToolkitDetectEvent(source: Toolkit) : EventObject(source)

    private suspend fun toolkitHostFlow(project: Project? = null): Flow<Pair<ToolkitHostType, Any>> = flow {
        val wslDistributions = scope.async { WslDistributionManager.getInstance().installedDistributions }
        val sshConfigs = scope.async { SshConfigManager.getInstance(project).configs }

        emit(LOCAL to SystemInfo.getOsName())
        if (WSLUtil.isSystemCompatible()) {
            wslDistributions.await().map {
                    emit(WSL to it)
            }
        }

        if (PlatformUtils.isCommercialEdition()) {
            sshConfigs.await().map {
                emit(SSH to it)
            }
        }
    }

    private suspend fun detectToolkitLocation(type: ToolkitHostType, target: Any?): Flow<String> = flow {
        val process = probeXmakeLocCommand.let {
            when (type) {
                LOCAL -> probeXmakeLocCommandOnWin.createLocalProcess()
                WSL -> it.createWslProcess(target as WSLDistribution)
                SSH -> it.createSshProcess(target as SshConfig)
            }
        }

        val (stdout, exitCode) = runProcess(process)
        val paths = stdout.getOrElse { "" }.split(Regex("\\r\\n|\\n|\\r"))
        Log.debug("ExitCode: $exitCode Output: $stdout")
        paths.forEach { emit(it) }
    }

    private suspend fun detectToolkitVersion(type: ToolkitHostType, target: Any?): Flow<String> = flow {
        val process = probeXmakeVersionCommand.let {
            when (type) {
                LOCAL -> it.createLocalProcess()
                WSL -> it.createWslProcess(target as WSLDistribution)
                SSH -> it.createSshProcess(target as SshConfig)
            }
        }
        val (stdout, exitCode) = runProcess(process)
        val versionString = stdout.getOrElse { "" }.split(Regex(",")).first().split(" ").last()
        Log.debug("ExitCode: $exitCode Version: $versionString")
        emit(versionString)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun detectXmakeToolkits(project: Project?) {
        detectionJob = scope.launch {
            val toolkitFlow = toolkitHostFlow(project)
            toolkitFlow.map { (type, target) ->
                detectToolkitLocation(type, target).flowOn(Dispatchers.IO).buffer().map { path ->
                    // Todo: path is not used fot detect version.
                    detectToolkitVersion(type, target).flowOn(Dispatchers.IO).buffer().map { versionString ->
                        when (type) {
                            LOCAL -> {
                                val name = SystemInfo.getOsName()
                                val host = ToolkitHost(LOCAL)
                                Toolkit(name, host, path, versionString)
                            }

                            WSL -> {
                                val wslDistribution = target as WSLDistribution
                                val name = wslDistribution.presentableName
                                val host = ToolkitHost(WSL, wslDistribution)
                                Toolkit(name, host, path, versionString)
                            }

                            SSH -> {
                                val sshConfig = (target as SshConfig)
                                val name = sshConfig.presentableShortName
                                val host = ToolkitHost(SSH, sshConfig)
                                Toolkit(name, host, path, versionString)
                            }
                        }.apply { this.isRegistered = true; this.isValid = true }
                    }
                }.flattenMerge().buffer()
            }.flattenMerge().flowOn(Dispatchers.Default).collect { toolkit ->
                // Todo: consider cache
                toolkitSet.add(toolkit)
                listenerList.forEach { listener ->
                    listener.onToolkitDetected(ToolkitDetectEvent(toolkit))
                }
            }
            listenerList.forEach { it.onAllToolkitsDetected() }
        }
    }

    fun cancelDetection() {
        scope.launch {
            detectionJob.cancel()
        }
    }

    // Todo: Validate toolkit.
    fun validateXmakeToolkit(){
        scope.launch {
            try {
                validateJob = launch { validateToolkits() }
            } catch (e: Exception) {
                Log.error("Error: ${e.message}")
            }
        }
    }

    // Todo: Validate toolkit.
    fun validateToolkits(){

    }

    // Todo: Validate toolkit.
    fun cancelValidation(){

    }

    fun addToolkitDetectedListener(listener: ToolkitDetectedListener) {
        listenerList.add(listener)
    }

    override fun getState(): State {
        return storage
    }

    override fun loadState(state: State) {
        state.registeredToolkits.forEach{
            registerToolkit(it)
            toolkitSet.add(it)
        }
        this.storage = state
    }

    fun loadToolkit(toolkit: Toolkit) {
        scope.launch(Dispatchers.IO) {
            toolkit.host.loadTarget()
            joinAll()
        }
    }

    fun registerToolkit(toolkit: Toolkit) {
        toolkit.isRegistered = true
        if (state.registeredToolkits.add(toolkit)){
            loadToolkit(toolkit)
        }
        Log.info("Registered toolkit: ${toolkit.name}, ${toolkit.id}")
    }

    // Todo: Increase robustness of this method
    fun unregisterToolkit(toolkit: Toolkit) {
        if(state.registeredToolkits.remove(toolkit)) {
            ProjectManager.getInstance().openProjects.forEach { project ->
                RunManager.getInstance(project).allConfigurationsList.forEach {
                    if (it is XMakeRunConfiguration) {
                        if (it.runToolkit?.id == toolkit.id)
                            it.runToolkit = null
                    }
                }
            }
        }
    }

    fun findRegisteredToolkitById(id: String): Toolkit? {
        return state.registeredToolkits.find { it.id == id }
    }

    companion object {
        fun  getInstance(): ToolkitManager = service()
        private val Log = logger<ToolkitManager>()
    }
}
