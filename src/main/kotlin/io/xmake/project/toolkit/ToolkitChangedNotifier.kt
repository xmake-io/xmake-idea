package io.xmake.project.toolkit

import com.intellij.util.messages.Topic

interface ToolkitChangedNotifier {

    fun toolkitChanged(toolkit: Toolkit?)

    companion object {
        @Topic.ProjectLevel
        val TOOLKIT_CHANGED_TOPIC: Topic<ToolkitChangedNotifier> = Topic.create(
            "toolkit changed",
            ToolkitChangedNotifier::class.java
        )
    }
}