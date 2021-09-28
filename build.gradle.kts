fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    //gradle-intellij-plugin
    id("org.jetbrains.intellij") version "1.1.4"
    //kotlin
    id("org.jetbrains.kotlin.jvm") version "1.5.10"
}

allprojects {
    apply {
        plugin("idea")
        plugin("kotlin")
        plugin("org.jetbrains.intellij")
    }

    repositories {
        mavenCentral()
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
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.21")
        testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.2")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.2")
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