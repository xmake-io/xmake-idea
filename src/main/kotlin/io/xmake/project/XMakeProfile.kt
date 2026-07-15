package io.xmake.project

/**
 * A named bundle of `xmake f` inputs, analogous to a CLion CMake profile. The build mode is
 * deliberately NOT part of a profile — it stays an orthogonal toolbar dropdown, so any profile
 * can be built in any mode.
 *
 * All properties are mutable `var`s with defaults so IntelliJ's XmlSerializer can round-trip the
 * class (no-arg constructor + bean-style access); data-class equality drives the Settings page's
 * modification check.
 */
data class XMakeProfile(
    var name: String = "",
    var platform: String = "default",
    var architecture: String = "default",
    var toolchain: String = "default",
    var buildDirectory: String = "",
    var additionalConfiguration: String = "",
    var androidNDKDirectory: String = "",
)
