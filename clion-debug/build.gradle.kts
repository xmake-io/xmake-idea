plugins {
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform.module")
}

group = "io.xmake.debug"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

intellijPlatform {
    dependencies {
        intellijPlatform {
            clion(providers.gradleProperty("runIdeVersion"))
            bundledPlugin("com.intellij.nativeDebug")
        }
    }
    
    // Disable plugin verification and runIDE for the debug module
    pluginVerification {
        ides { }
    }
}

// Disable buildSearchableOptions for CLion module only (due to traverseUI issues)
tasks.matching { task -> task.name.contains("buildSearchableOptions") }.configureEach {
    enabled = false
}

// Disable runIde for CLion module (should not run IDE from debug module)
tasks.matching { task -> task.name.contains("runIde") }.configureEach {
    enabled = false
}

// This module does not contain tests; disabling test-related IntelliJ tasks avoids
// pulling its sandbox preparation into the root `test` task graph.
tasks.matching { task ->
    task.name in setOf(
        "compileTestKotlin",
        "compileTestJava",
        "processTestResources",
        "testClasses",
        "instrumentTestCode",
        "prepareTestSandbox",
        "prepareTest",
        "test"
    )
}.configureEach {
    enabled = false
}
