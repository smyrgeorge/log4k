plugins {
    id("io.github.smyrgeorge.log4k.multiplatform")
    id("io.github.smyrgeorge.log4k.publish")
    id("io.github.smyrgeorge.log4k.dokka")
}

kotlin {
    sourceSets {
        all {
            languageSettings.enableLanguageFeature("ContextParameters")
        }
        configureEach {
            languageSettings.progressiveMode = true
        }
        commonMain {
            dependencies {
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