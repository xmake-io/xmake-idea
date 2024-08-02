package io.xmake.project

import io.xmake.project.toolkit.Toolkit

data class XMakeConfigData(
    val languagesModel: String,
    val kindsModel: String,
    val toolkit: Toolkit?,
    val remotePath: String? = null,
)