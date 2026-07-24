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
    jvmToolchain(25)
}

dependencies {
    intellijPlatform {
        clion(providers.gradleProperty("runIdeVersion"))
        bundledPlugin("com.intellij.nativeDebug")
    }
}
