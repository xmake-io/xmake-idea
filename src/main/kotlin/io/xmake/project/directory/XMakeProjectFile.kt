package io.xmake.project.directory

import com.intellij.openapi.project.Project
import java.io.File

/** Whether the IDE project root itself contains a root xmake.lua. */
internal val Project.hasRootXMakeLua: Boolean
    get() = basePath?.let(::hasRootXMakeLua) == true

/** Checks a local directory for the root XMake project file. */
internal fun hasRootXMakeLua(directory: String): Boolean =
    File(directory, "xmake.lua").isFile
