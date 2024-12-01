plugins {
    id("io.github.smyrgeorge.log4k.multiplatform.examples")
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        val commonMain by getting {
            dependencies {
                implementation(project(":log4k"))
                implementation(project(":log4k-coroutines"))
            }
        }
    }
}