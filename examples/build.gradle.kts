plugins {
    id("io.github.smyrgeorge.log4k.multiplatform")
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        val commonMain by getting {
            dependencies {
                implementation(project(":log4k"))
            }
        }
    }
}