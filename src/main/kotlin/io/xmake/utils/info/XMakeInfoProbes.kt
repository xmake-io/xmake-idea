/*!A Xmake integration in IntelliJ IDEA/Clion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (C) 2015-present, Xmake Open Source Community.
 *
 * @file        XMakeInfoProbes.kt
 *
 */
package io.xmake.utils.info

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.execution.ExecutionException
import com.intellij.openapi.project.Project
import io.xmake.project.toolkit.Toolkit
import io.xmake.project.toolkit.ToolkitHostType.LOCAL
import io.xmake.project.toolkit.ToolkitInstallationKey
import io.xmake.project.toolkit.installationKey
import io.xmake.utils.SystemUtils
import io.xmake.utils.cli.QUERY_ENVIRONMENT
import io.xmake.utils.cli.XMakeCommandLine
import io.xmake.utils.cli.xmake
import kotlinx.coroutines.CancellationException
import kotlin.time.Duration

/** Lists the project templates of [toolkit]'s installation; xmake 3.x answers with an
 *  indented group/language/template tree. */
private fun xmakeTemplateListingCommand(toolkit: Toolkit): XMakeCommandLine = xmake(toolkit).apply {
    listTemplates()
    environment.putAll(QUERY_ENVIRONMENT)
}

/** Exports the templates of a 2.x installation through `core.project.template`; the module
 *  was removed in xmake 3.x along with the old template engine, so the two listing paths
 *  never answer for the same installation. */
private fun xmakeTemplateExportCommand(toolkit: Toolkit): XMakeCommandLine {
    val scriptPath = SystemUtils.getScriptPath("templates.lua")
        ?: throw ExecutionException("The template listing script was not found")
    return xmake(toolkit).apply {
        lua { script = scriptPath }
        environment.putAll(QUERY_ENVIRONMENT)
    }
}

/** Lists the project templates of [toolkit]'s installation with no project context, so the
 *  wizard can run it before a project exists: `xmake create --list` first, then the lua
 *  exporter for installations that reject the flag. Returns null when neither command
 *  lists templates, leaving callers on their built-in templates. */
internal suspend fun probeXMakeProjectTemplates(
    toolkit: Toolkit,
    project: Project?,
    timeout: Duration,
): XMakeProjectTemplates? {
    val key = toolkit.installationKey()
    if (key !in EXPORTER_LISTING_TOOLKITS) {
        when (val listing = executeTemplateProbe(toolkit, project, timeout, ::xmakeTemplateListingCommand)) {
            is TemplateListing.Templates -> return listing.templates
            TemplateListing.Rejected -> EXPORTER_LISTING_TOOLKITS.add(key)
            TemplateListing.Transient -> return null
        }
    }
    // The lua exporter runs a script by local path, so only the local host can take it; a
    // remote 2.x installation falls back to the built-in templates instead.
    if (toolkit.host.type != LOCAL) return null
    return when (val listing = executeTemplateProbe(toolkit, project, timeout, ::xmakeTemplateExportCommand)) {
        is TemplateListing.Templates -> listing.templates
        else -> null
    }
}

/** Installations (xmake 2.x) that answer through the lua exporter, because `create --list` was rejected by a completed command;
 *  transient failures never land here. */
private val EXPORTER_LISTING_TOOLKITS = java.util.concurrent.ConcurrentHashMap.newKeySet<ToolkitInstallationKey>()

/** The outcome of one template listing command: [Templates] read, a completed run that was
 *  [Rejected], or a [Transient] failure that produced no answer. */
private sealed interface TemplateListing {
    data class Templates(val templates: XMakeProjectTemplates) : TemplateListing
    data object Rejected : TemplateListing
    data object Transient : TemplateListing
}

private suspend fun executeTemplateProbe(
    toolkit: Toolkit,
    project: Project?,
    timeout: Duration,
    command: (Toolkit) -> XMakeCommandLine,
): TemplateListing = try {
    val result = command(toolkit).execute(project, timeout)
    if (result.exitCode != 0) {
        Log.debug("XMake template listing rejected (exit=${result.exitCode}): ${result.stdErr.trim()}")
        TemplateListing.Rejected
    } else {
        val templates = XMakeInfo.parseProjectTemplates(result.stdOut)
        if (templates.isEmpty()) TemplateListing.Transient else TemplateListing.Templates(templates)
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Log.debug("XMake template listing failed on ${toolkit.name}", error)
    TemplateListing.Transient
}

private val Log = fileLogger()
