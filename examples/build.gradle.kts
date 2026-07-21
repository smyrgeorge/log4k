plugins {
    id("io.github.smyrgeorge.log4k.multiplatform.examples")
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        commonMain {
            dependencies {
                implementation(project(":log4k-classic"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}

// Wire the log4k-compiler-plugin IR plugin (@Trace instrumentation) onto every Kotlin
// compiler-plugin classpath, so it runs for all targets and the common metadata compilation. This
// is manual wiring until a dedicated KotlinCompilerPluginSupportPlugin is (re)introduced.
afterEvaluate {
    configurations.names
        .filter { it.startsWith("kotlinCompilerPluginClasspath") }
        .forEach { cfg ->
            dependencies.add(cfg, project(":log4k-compiler-plugin"))
        }
}
