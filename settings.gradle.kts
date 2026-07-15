// Auto-provisions the JDK requested by the jvmToolchain(...) setting, so builds use the same
// JDK 21 as CI regardless of the locally installed Java version.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "xmake-idea"

include(":clion-debug")
