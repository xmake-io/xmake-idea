fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    //gradle-intellij-plugin
    id("org.jetbrains.intellij") version "1.1.4"
    //kotlin
    id("org.jetbrains.kotlin.jvm") version "1.5.10"
}

apply{
    plugin("org.jetbrains.intellij")
    plugin("idea")
}

//group("io.xmake")
//version("1.0.6")
group = properties("pluginGroup")
version = properties("pluginVersion")

repositories {
    mavenCentral()
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
    pluginName.set(properties("pluginName"))
    version.set(properties("platformVersion"))
    type.set(properties("platformType"))
    downloadSources.set(true)
    updateSinceUntilBuild.set(true)
    plugins.set(properties("platformPlugins").split(',').map(String::trim).filter(String::isNotEmpty))
}

tasks {
    withType<Test> {
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