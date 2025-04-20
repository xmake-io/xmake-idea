package io.xmake.utils.info

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XMakeApis(
    @SerialName("description_builtin_apis")
    val descriptionBuiltinApis: List<String>,
    @SerialName("description_builtin_module_apis")
    val descriptionBuiltinModuleApis: List<String>,
    @SerialName("script_instance_apis")
    val scriptInstanceApis: List<String>,
    @SerialName("script_extension_module_apis")
    val scriptExtensionModuleApis: List<String>,
    @SerialName("description_scope_apis")
    val descriptionScopeApis: List<String>,
    @SerialName("script_builtin_apis")
    val scriptBuiltinApis: List<String>,
    @SerialName("script_builtin_module_apis")
    val scriptBuiltinModuleApis: List<String>,
)
typealias XMakeArchitectures = Map<String, List<String>>
typealias XMakeBuildModes = List<String>
typealias XMakeEnvs = Map<String, String>
typealias XMakePackages = List<String>
typealias XMakePlatforms = List<String>
typealias XMakePolicies = Map<String, Any>
typealias XMakeRules = List<String>
typealias XMakeTargets = List<String>
typealias XMakeToolchains = Map<String, String>