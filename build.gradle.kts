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

    dependencies {
        // Default to CLion for development
        intellijPlatform {
            clion(properties("runIdeVersion"))
            bundledPlugin("com.intellij.nativeDebug")
            testFramework(TestFrameworkType.Platform)
        }
    }
    
    pluginVerification {
        ides {
            create(IntelliJPlatformType.CLion, properties("runIdeVersion")) {}
            create(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3") {}
        }
    }
}

tasks {
    test {
        useJUnit()
        include("io/xmake/**/**")
    }
}

// Disable buildSearchableOptions (due to CLion traverseUI issues)
tasks.matching { task -> task.name.contains("buildSearchableOptions") }.configureEach {
    enabled = false
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("junit:junit:4.13.2")
}

// Add compilation order dependency - build clion-debug first
tasks.named("compileKotlin") {
    dependsOn(":clion-debug:build", ":clion-debug:copyToPluginResources")
}

tasks.named("build") {
    dependsOn(":clion-debug:build", ":clion-debug:copyToPluginResources")
}

// Also ensure all CLion tasks complete before main plugin compilation
tasks.named("classes") {
    dependsOn(":clion-debug:build", ":clion-debug:copyToPluginResources")
}

tasks.named("jar") {
    dependsOn(":clion-debug:build", ":clion-debug:copyToPluginResources")
}

val Project.dependencyCachePath
    get(): String {
        val cachePath = file("${rootProject.projectDir}/deps")
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        return cachePath.absolutePath
    }