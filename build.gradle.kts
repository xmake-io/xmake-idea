import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun properties(key: String) = project.findProperty(key).toString()

//read local workspace file to string
val localChangeNotes: String = file("${projectDir}/change-notes.html").readText(Charsets.UTF_8)
val localDescription: String = file("${projectDir}/description.html").readText(Charsets.UTF_8)

//testing ide (true : clion , false : intellij)
//val testIde:String = if(properties("testInClion").toBoolean()) "CL" else "IC"
val testIde:String = when(1) {
    0-> "IC"
    1-> "IU"
    2-> "CL"
    else -> "IC"
}


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
    type.set(testIde)
    version.set("2024.1.4")
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
                "2022.3",
                "2023.1",
                "2023.2",
                "2023.3",
                "2024.1 EAP",
            )
        )
    }
    runIde {
        ideDir.set(file("deps/ideaIU-2024.1.4"))
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.0.0")
    testImplementation("io.mockk:mockk:1.13.10")
}



val Project.dependencyCachePath
    get(): String {
        val cachePath = file("${rootProject.projectDir}/deps")
        if (!cachePath.exists()) {
            cachePath.mkdirs()
        }
        return cachePath.absolutePath
    }