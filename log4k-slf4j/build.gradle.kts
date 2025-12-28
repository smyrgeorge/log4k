plugins {
    id("io.github.smyrgeorge.log4k.multiplatform.jvm")
    id("io.github.smyrgeorge.log4k.publish")
    id("io.github.smyrgeorge.log4k.dokka")
}

kotlin {
    explicitApi()
    sourceSets {
        configureEach {
            languageSettings.progressiveMode = true
        }
        jvmMain {
            dependencies {
                api(project(":log4k"))
                api(libs.slf4j)
            }
        }
        jvmTest {
            dependencies {
            }
        }
    }
}