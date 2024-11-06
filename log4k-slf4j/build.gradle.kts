plugins {
    id("io.github.smyrgeorge.log4k.multiplatform.jvm")
    id("io.github.smyrgeorge.log4k.publish")
}

kotlin {
    explicitApi()
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        val jvmMain by getting {
            dependencies {
                api(project(":log4k"))
                api(libs.slf4j)
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }
    }
}