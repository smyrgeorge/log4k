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
                implementation(project(":log4k"))
                implementation(project(":log4k-coroutines"))
            }
        }
    }
}