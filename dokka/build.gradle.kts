plugins {
    id("io.github.smyrgeorge.log4k.dokka")
}

dependencies {
    dokka(project(":log4k"))
    dokka(project(":log4k-coroutines"))
    dokka(project(":log4k-slf4j"))
}

dokka {
    moduleName.set(rootProject.name)
}
