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
    compileOnly(project(":"))

    intellijPlatform {
        clion(providers.gradleProperty("runIdeVersion"))
        bundledModule("intellij.clion.execution")
        bundledModule("intellij.clion.toolchains")
        bundledModule("intellij.cidr.runner")
        bundledModule("intellij.cidr.execution")
        bundledModule("intellij.cidr.projectModel")
        // Compile-time only: CLionLauncher's inherited signatures mention CidrDebugProfile. This
        // module is JetBrains-internal, so it must never be declared in xmake-idea.clion-run.xml.
        bundledModule("intellij.cidr.debugger.profiles")
    }
}
