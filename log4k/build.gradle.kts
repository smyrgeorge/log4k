plugins {
    id("io.github.smyrgeorge.log4k.multiplatform")
    id("io.github.smyrgeorge.log4k.publish")
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        @Suppress("unused")
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialisation.json)
            }
        }

        @Suppress("unused")
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.assertk)
            }
        }
    }
}