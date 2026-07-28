plugins {
    id("io.github.smyrgeorge.log4k.dokka")
}

dependencies {
    dokka(project(":log4k"))
    dokka(project(":log4k-classic"))
    dokka(project(":log4k-compiler-plugin"))
    dokka(project(":log4k-context"))
    dokka(project(":log4k-integrations"))
    dokka(project(":log4k-slf4j"))
    dokka(project(":log4k-slf4j-appender"))
}

dokka {
    moduleName.set(rootProject.name)
}
