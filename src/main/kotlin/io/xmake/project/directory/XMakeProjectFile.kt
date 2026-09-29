package io.xmake.project.directory

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import java.io.File

/** Whether the IDE project root itself contains a root xmake.lua. */
internal val Project.hasRootXMakeLua: Boolean
    get() = basePath?.let(::hasRootXMakeLua) == true

/** Checks a local directory for the root XMake project file. */
internal fun hasRootXMakeLua(directory: String): Boolean =
    File(directory, "xmake.lua").isFile

/** Whether [event] creates, deletes, moves, or renames an xmake.lua file. Deliberately
 *  over-inclusive: an xmake.lua outside the IDE project may back a configured local directory,
 *  so filtering by project membership would miss relevant changes. */
internal fun isXMakeLuaVfsEvent(event: VFileEvent): Boolean =
    when (event) {
        is VFileCreateEvent, is VFileDeleteEvent, is VFileMoveEvent ->
            event.file?.name?.equals("xmake.lua", ignoreCase = true) == true

        is VFilePropertyChangeEvent ->
            event.propertyName == VirtualFile.PROP_NAME &&
                    listOf(event.oldValue, event.newValue).any { value ->
                        value is String && value.equals("xmake.lua", ignoreCase = true)
                    }

        else -> false
    }
