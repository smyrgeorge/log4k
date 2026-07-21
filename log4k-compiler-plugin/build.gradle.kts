plugins {
    id("io.github.smyrgeorge.log4k.multiplatform.jvm")
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        jvmMain {
            dependencies {
                // Provided by the Kotlin compiler at runtime — must NOT be bundled.
                compileOnly(libs.kotlin.compiler.embeddable)
            }
        }
    }
}
