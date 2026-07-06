plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.7.2"
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
            clion("2026.1.1")
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

// The root plugin artifact is the verifier boundary; this module is packaged into it.
tasks.matching { task -> task.name == "verifyPlugin" }.configureEach {
    enabled = false
}

tasks {
    compileKotlin {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    
    compileJava {
        options.release.set(17)
    }
    
    jar {
        archiveBaseName.set("xmake-clion-debug")
        archiveVersion.set("")
        archiveClassifier.set("")
        
        // Include all dependencies in the JAR so it's self-contained
        from({
            configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
        }) {
            exclude("META-INF/*.SF")
            exclude("META-INF/*.DSA")
            exclude("META-INF/*.RSA")
        }
        
        manifest {
            attributes(
                "Main-Class" to "io.xmake.debug.clion.ClionDebugModule",
                "Implementation-Title" to "XMake CLion Debug Module",
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "XMake"
            )
        }
    }
    
    // Create a task to copy the JAR to the main plugin resources/lib
    register<Copy>("copyToPluginResources") {
        dependsOn(jar)
        from(jar.get())
        into("../src/main/resources/lib")
    }
}
