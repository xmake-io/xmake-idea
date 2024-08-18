package io.xmake.project.toolkit

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import io.xmake.run.XMakeRunConfiguration

val Project.activatedToolkit: Toolkit?
    get() = RunManager.getInstance(this).selectedConfiguration?.configuration.let {
        if (it is XMakeRunConfiguration) it.runToolkit else null
    }