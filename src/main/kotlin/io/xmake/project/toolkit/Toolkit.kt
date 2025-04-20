package io.xmake.project.toolkit

import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.Transient
import java.util.*

@Tag("toolkit")
data class Toolkit(
    @Attribute
    val name: String = "",
    @Property(surroundWithTag = false)
    val host: ToolkitHost = ToolkitHost(ToolkitHostType.LOCAL),
    @Attribute
    val path: String = "",
    @Attribute
    val version: String = "",
) {
    @Attribute
    val id: String = UUID.nameUUIDFromBytes((name+host.type.name+path+version).toByteArray()).toString()
    @get:Transient
    var isRegistered: Boolean = false
    @get:Transient
    var isValid: Boolean = true
    @get:Transient
    val isOnRemote: Boolean
        get() = with(host) { type == ToolkitHostType.WSL || type == ToolkitHostType.SSH }
}