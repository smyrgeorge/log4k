plugins {
    id("io.github.smyrgeorge.log4k.multiplatform")
    id("io.github.smyrgeorge.log4k.publish")
    id("io.github.smyrgeorge.log4k.dokka")
}

kotlin {
    @Suppress("unused")
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialisation.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.assertk)
            }
        }
    }
}