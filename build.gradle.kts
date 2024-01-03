fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    //gradle-intellij-plugin
    id("org.jetbrains.intellij") version "1.16.1"
    //kotlin
    id("org.jetbrains.kotlin.jvm") version "1.9.21"

    id("org.jetbrains.changelog") version "2.2.0"
}

allprojects {
    apply {
        plugin("idea")
        plugin("kotlin")
        plugin("org.jetbrains.intellij")
    }

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.aliyun.com/repository/public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
    }

    configurations {
        all {
            // Allows using project dependencies instead of IDE dependencies during compilation running
            resolutionStrategy.sortArtifacts(ResolutionStrategy.SortOrder.DEPENDENCY_FIRST)
        }
    }

    // See https://github.com/JetBrains/gradle-intellij-plugin/
    intellij {
        pluginName.set(properties("pluginName"))
        version.set(properties("baseVersion"))
        //type.set(properties("platformType"))
        downloadSources.set(properties("platformDownloadSources").toBoolean())
        ideaDependencyCachePath.set(dependencyCachePath)
        updateSinceUntilBuild.set(true)
        plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
    }

    tasks {
        withType<JavaCompile> {
            sourceCompatibility = "17"
            targetCompatibility = "17"
        }
        withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            kotlinOptions.jvmTarget = "17"
        }

        test {
            useJUnitPlatform()
        }
        getByName("buildSearchableOptions").enabled = false

        patchPluginXml {
            version.set(properties("pluginVersion"))
            sinceBuild.set(properties("pluginSinceBuild"))
            untilBuild.set(properties("pluginUntilBuild"))
        }

        runPluginVerifier {
            ideVersions.set(properties("pluginVerifierIdeVersions").split(',').map(String::trim).filter(String::isNotEmpty))
        }

    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.21")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
    }

    // Set the JVM language level used to build the project. Use Java 11 for 2020.3+, and Java 17 for 2022.2+.
    kotlin {
        @Suppress("UnstableApiUsage")
        jvmToolchain {
            languageVersion = JavaLanguageVersion.of(17)
            vendor = JvmVendorSpec.JETBRAINS
        }
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


project(":clion") {
    intellij {
        version.set(properties("clionVersion"))
        plugins.set(properties("clionPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
    }
    dependencies {
        implementation(project(":"))
    }
}