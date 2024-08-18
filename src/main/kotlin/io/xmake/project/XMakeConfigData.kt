package io.xmake.project

import io.xmake.project.toolkit.Toolkit

@Deprecated("Please refer to the relevant content in folder io/xmake/project/wizard.")
data class XMakeConfigData(
    val languagesModel: String,
    val kindsModel: String,
    val toolkit: Toolkit?,
    val remotePath: String? = null,
)