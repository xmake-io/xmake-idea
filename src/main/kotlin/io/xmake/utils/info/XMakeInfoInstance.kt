package io.xmake.utils.info

import com.intellij.openapi.project.Project

val Project.xmakeInfo: XMakeInfo
    get() = XMakeInfoManager.getInstance(this).xmakeInfo