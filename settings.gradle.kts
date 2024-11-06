rootProject.name = "log4k"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    includeBuild("build-logic")
}

include("log4k")
include("log4k-slf4j")
include("examples")

