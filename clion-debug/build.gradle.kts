plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform.module")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        clion(providers.gradleProperty("runIdeVersion"))
        bundledPlugin("com.intellij.nativeDebug")
        // Compilation Database ExternalSystem — used to feed IntelliSense from
        // xmake's generated compile_commands.json (io.xmake.debug.clion.CompDBIntegration).
        bundledPlugin("com.intellij.clion-compdb")
    }
}
