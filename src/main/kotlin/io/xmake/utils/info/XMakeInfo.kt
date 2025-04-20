package io.xmake.utils.info

import com.intellij.openapi.diagnostic.logger
import kotlinx.serialization.json.Json

class XMakeInfo {

    var apis: XMakeApis? = null
    var architectures: XMakeArchitectures = emptyMap()
    var buildModes: XMakeBuildModes = emptyList()
    var envs: XMakeEnvs = emptyMap()
    var packages: XMakePackages = emptyList()
    var platforms: XMakePlatforms = emptyList()
    var policies: XMakePolicies = emptyMap()
    var rules: XMakeRules = emptyList()
    var targets: XMakeTargets = emptyList()
    var toolchains: XMakeToolchains = emptyMap()

    fun parseApis(apiString: String): XMakeApis? {
        return Json.decodeFromString(apiString)
    }

    fun parseArchitectures(archString: String): XMakeArchitectures {
        return Json.decodeFromString(archString)
    }

    fun parseBuildModes(buildModeString: String): XMakeBuildModes {
        return Json.decodeFromString(buildModeString)
    }

    fun parseEnvs(envString: String): XMakeEnvs {
        // Todo
        return emptyMap()
    }

    fun parsePackages(packageString: String): XMakePackages {
        // Todo
        return emptyList()
    }

    fun parsePlatforms(platformString: String): XMakePlatforms {
        return Json.decodeFromString(platformString)
    }

    fun parsePolicies(policyString: String): XMakePolicies {
        // Todo
        return emptyMap()
    }

    fun parseRules(ruleString: String): XMakeRules {
        return Json.decodeFromString(ruleString)
    }

    fun parseTargets(targetString: String): XMakeTargets {
        return Json.decodeFromString(targetString)
    }

    fun parseToolchains(toolchainString: String): XMakeToolchains {
        return toolchainString.split("\n").associate {
            it.split(" ", limit = 2).map { s ->
                s.replace(Regex("\u001B\\[[0-9;]*[A-Za-z]"), "")
            }.let { (toolchain, description) ->
                toolchain to description
            }
        }.also { Log.info("Parsed XMake Supported Toolchains: ${it.keys}") }
    }

    companion object {
        val Log = logger<XMakeInfo>()

    }
}

