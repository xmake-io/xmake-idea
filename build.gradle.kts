import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

fun properties(key: String) = project.findProperty(key).toString()

// read local workspace file to string
val localChangeNotes: String = file("${projectDir}/change-notes.html").readText(Charsets.UTF_8)
val localDescription: String = file("${projectDir}/description.html").readText(Charsets.UTF_8)

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.7.2"
    id("org.jetbrains.kotlin.jvm") version "2.1.0"
    id("org.jetbrains.changelog") version "2.2.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "io.xmake"

repositories {
    maven("https://maven.aliyun.com/repository/public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
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

    pluginVerification {
        ides {
            select {
                types = listOf(
                    IntelliJPlatformType.CLion,
                    IntelliJPlatformType.IntellijIdeaCommunity,
                    IntelliJPlatformType.IntellijIdeaUltimate,
                    IntelliJPlatformType.AndroidStudio
                )
                sinceBuild = properties("pluginSinceBuild")
            }
        }
    }
}

tasks {
    test {
        useJUnit()
        include("io/xmake/**/**")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        // Default to CLion for development, but allow other IDEs for verification
        clion(properties("runIdeVersion"))
        bundledPlugin("com.intellij.nativeDebug")
        testFramework(TestFrameworkType.Platform)
    }
}

val Project.dependencyCachePath
    get(): String {
        val cachePath = file("${rootProject.projectDir}/deps")
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        return cachePath.absolutePath
    }