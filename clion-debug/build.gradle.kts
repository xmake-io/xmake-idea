plugins {
    kotlin("jvm")
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
            clion("2025.3")
            bundledPlugin("com.intellij.nativeDebug")
        }
    }
    
    // Disable plugin verification and runIDE for the debug module
    pluginVerification {
        ides { }
    }
}

// Disable runIDE task for the debug module
tasks.named("runIde") {
    enabled = false
}

// Disable prepareJarSearchableOptions task for the debug module
tasks.named("prepareJarSearchableOptions") {
    enabled = false
}

tasks {
    compileKotlin {
        compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    
    jar {
        archiveBaseName.set("xmake-clion-debug")
        
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
    
    // Create a task to copy the JAR to the main plugin resources
    register<Copy>("copyToPluginResources") {
        dependsOn(jar)
        from(jar.get())
        into("../main/resources")
    }
}
