import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

val pluginVersion = providers.gradleProperty("pluginVersion")
val pluginSinceBuild = providers.gradleProperty("pluginSinceBuild")
val runIdeVersion = providers.gradleProperty("runIdeVersion")
val localChangeNotes = providers.fileContents(layout.projectDirectory.file("change-notes.html")).asText
val localDescription = providers.fileContents(layout.projectDirectory.file("description.html")).asText

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.changelog") version "2.5.0"
}

group = "io.xmake"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    pluginConfiguration {
        version = pluginVersion
        changeNotes = localChangeNotes
        description = localDescription
        ideaVersion {
            sinceBuild = pluginSinceBuild
        }
    }

    pluginVerification.ides {
        select {
            types = listOf(
                IntelliJPlatformType.CLion,
                IntelliJPlatformType.IntellijIdea
            )
            sinceBuild = pluginSinceBuild
        }
    }
}

dependencies {
    intellijPlatform {
        clion(runIdeVersion)
        bundledPlugin("com.intellij.nativeDebug")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

tasks {
    test {
        useJUnit()
        include("io/xmake/**/**")
    }
}

// Keep the existing CLion debug jar packaging until it is migrated to a plugin module.
tasks.named("compileKotlin") {
    dependsOn(":clion-debug:build", ":clion-debug:copyToPluginResources")
}

tasks.named("build") {
    dependsOn(":clion-debug:build", ":clion-debug:copyToPluginResources")
}

tasks.named("classes") {
    dependsOn(":clion-debug:build", ":clion-debug:copyToPluginResources")
}

tasks.named("processResources") {
    dependsOn(":clion-debug:copyToPluginResources")
}

tasks.named("jar") {
    dependsOn(":clion-debug:build", ":clion-debug:copyToPluginResources")
}
