import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

// read local workspace file to string
val localChangeNotes: String = file("${projectDir}/change-notes.html").readText(Charsets.UTF_8)
val localDescription: String = file("${projectDir}/description.html").readText(Charsets.UTF_8)

val type = mapOf(
    "IC" to "ideaIC",
    "IU" to "ideaIU",
    "CL" to "clion",
    "PY" to "pycharmPY"
)

/*
* Best practice:
* Use CL for both building and running.
* If you lack a license, use CLI for building and IC for running.
* Specify the ideDir path as needed.
* */

val buildIdeType: String = when (2) {
    0 -> "IC" // SSH-related functions cannot be built by the Community version.
    1 -> "IU" // To build with Ultimate version does not require a license.
    2 -> "CL" // C/C++ intellij-sense is included.
    3 -> "PY"
    else -> "IC"
}

val buildIdeVersion = "2024.3"

val runIdeType: String = when (2) {
    0 -> "IC" // You can build with the Ultimate version, but run with the Community version.
    1 -> "IU" // It may require a license to run with the Ultimate version.
    2 -> "CL"  // It includes C/C++ related functions, along with functions in the Ultimate version.
    3 -> "PY"
    else -> "IC"
}

val runIdeVersion = "2024.3"

plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.17.1"
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.changelog") version "2.2.0"
}

group = "io.xmake"

repositories {
    maven("https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository")
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.aliyun.com/repository/public/")
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

intellij {
    type.set(buildIdeType)
    version.set(buildIdeVersion)
    downloadSources.set(true)
    ideaDependencyCachePath.set(dependencyCachePath)
    updateSinceUntilBuild.set(true)
    /*
    plugins.set(
        listOf(
            "com.intellij.clion",
            "com.intellij.cidr.base",
            "com.intellij.cidr.lang"
        )
    )
     */
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
        kotlinOptions.languageVersion = "2.0"
    }

    test {
        useJUnit()
        include("io/xmake/**/**")
    }

    patchPluginXml {
        version = properties("pluginVersion")
        sinceBuild = properties("pluginSinceBuild")
        untilBuild = properties("pluginUntilBuild")
        changeNotes = localChangeNotes
        pluginDescription = localDescription
    }

    runPluginVerifier {
        ideVersions.set(
            listOf(
                "2023.3",
                "2024.1",
                "2024.2",
                "2024.3"
            )
        )
    }

    // Execute this downloadIde gradle task if missing build.txt in runIde task.
    register("downloadIde") {
        group = "Custom Tasks"
        description = "Downloads a specific version and type of IntelliJ IDEA based on provided parameters."

        doFirst {
            println("Executing downloadIde task")
            val intellijExtension = project.extensions.getByType(IntelliJPluginExtension::class.java)
            val ideVersion = project.findProperty("ideVersion")?.toString() ?: runIdeVersion
            val ideType = project.findProperty("ideType")?.toString() ?: runIdeType

            intellijExtension.version.set(ideVersion)
            intellijExtension.type.set(ideType)
        }

        finalizedBy("setupDependencies")
    }

    runIde {
        ideDir.set(file("deps/${type[runIdeType]}-$runIdeVersion"))
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")
    testImplementation("io.mockk:mockk:1.13.12")
}

val Project.dependencyCachePath
    get(): String {
        val cachePath = file("${rootProject.projectDir}/deps")
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        return cachePath.absolutePath
    }
