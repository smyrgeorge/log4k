plugins {
    id("io.github.smyrgeorge.log4k.multiplatform")
    alias(libs.plugins.android)
    id("io.github.smyrgeorge.log4k.publish")
    id("io.github.smyrgeorge.log4k.dokka")
}

kotlin {
    android {
        namespace = "io.github.smyrgeorge.log4k"
        compileSdk = 36
        minSdk = 24
    }
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        commonMain {
            dependencies {
                api(project(":log4k"))
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialisation.json)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.assertk)
            }
        }
    }
}