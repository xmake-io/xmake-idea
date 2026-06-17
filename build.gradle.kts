import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String) = project.findProperty(key).toString()

// read local workspace file to string
val localChangeNotes: String = file("${projectDir}/change-notes.html").readText(Charsets.UTF_8)
val localDescription: String = file("${projectDir}/description.html").readText(Charsets.UTF_8)

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.16.0"
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.changelog") version "2.5.0"
    kotlin("plugin.serialization") version "2.3.20"
}

group = "io.xmake"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    pluginConfiguration {
        version = properties("pluginVersion")
        changeNotes = localChangeNotes
        description = localDescription
        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
        }
    }

    caching.ides {
        enabled = true
        path = layout.projectDirectory.dir(".intellijPlatform/ides")
        name = { requested -> "${requested.type}-${requested.version}" }
    }

    pluginVerification.ides {
        select {
            types = listOf(
                IntelliJPlatformType.CLion,
                IntelliJPlatformType.IntellijIdeaCommunity
            )
            sinceBuild = properties("pluginSinceBuild")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    intellijPlatform {
        clion(properties("runIdeVersion"))
        bundledPlugin("com.intellij.nativeDebug")
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

val prepareClionDebugResources by tasks.registering {
    group = "build"
    description = "Builds the CLion debug module and copies it into plugin resources."
    dependsOn(":clion-debug:build", ":clion-debug:copyToPluginResources")
}

tasks {
    matching { task -> task.name.contains("buildSearchableOptions") }.configureEach {
        enabled = false
    }

    matching { task ->
        task.name in setOf("buildPlugin", "prepareSandbox", "runIde")
    }.configureEach {
        dependsOn(prepareClionDebugResources)
    }

    test {
        useJUnit()
        include("io/xmake/**/**")
    }
}
