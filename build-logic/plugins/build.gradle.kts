plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        create("multiplatform") {
            id = "io.github.smyrgeorge.log4k.multiplatform"
            implementationClass = "io.github.smyrgeorge.log4k.multiplatform.MultiplatformConventions"
        }
        create("multiplatform.jvm") {
            id = "io.github.smyrgeorge.log4k.multiplatform.jvm"
            implementationClass = "io.github.smyrgeorge.log4k.multiplatform.MultiplatformJvmConventions"
        }
        create("multiplatform.examples") {
            id = "io.github.smyrgeorge.log4k.multiplatform.examples"
            implementationClass = "io.github.smyrgeorge.log4k.multiplatform.MultiplatformExamplesConventions"
        }
        create("publish") {
            id = "io.github.smyrgeorge.log4k.publish"
            implementationClass = "io.github.smyrgeorge.log4k.publish.PublishConventions"
        }
    }
}

dependencies {
    compileOnly(libs.gradle.kotlin.plugin)
    compileOnly(libs.gradle.publish.plugin)
}
