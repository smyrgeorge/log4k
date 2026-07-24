import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    id("io.github.smyrgeorge.log4k.publish")
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
    }
}

java {
    // Gradle plugins must target the JVM used by the consumer's Gradle daemon.
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Provided by the consumer's Kotlin Gradle plugin at runtime — must NOT be bundled.
    compileOnly(libs.kotlin.gradle.plugin.api)
}

gradlePlugin {
    plugins {
        create("log4k") {
            id = "io.github.smyrgeorge.log4k"
            implementationClass = "io.github.smyrgeorge.log4k.gradle.Log4kGradlePlugin"
            displayName = "log4k Gradle plugin"
            description =
                "Wires the log4k Kotlin compiler plugin (@Logged, @Timed, @Traced) onto every Kotlin compilation."
        }
    }
}

// Generate a BuildConfig carrying this module's version, so the plugin can request the matching
// log4k-compiler-plugin artifact via SubpluginArtifact at consumer build time.
val generatedSourcesDir = layout.buildDirectory.dir("generated/log4k/kotlin")
val generateBuildConfig = tasks.register("generateBuildConfig") {
    group = "build"
    description = "Generates BuildConfig.kt carrying this module's version for SubpluginArtifact resolution."
    val version = project.version.toString()
    val outputDir = generatedSourcesDir
    inputs.property("version", version)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("io/github/smyrgeorge/log4k/gradle/BuildConfig.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.github.smyrgeorge.log4k.gradle

            internal object BuildConfig {
                const val VERSION: String = "$version"
            }
            """.trimIndent() + "\n"
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateBuildConfig)
}
