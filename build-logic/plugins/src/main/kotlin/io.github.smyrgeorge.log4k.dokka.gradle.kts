import org.jetbrains.dokka.gradle.DokkaExtension

plugins {
    id("org.jetbrains.dokka")
}

configure<DokkaExtension> {
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(project.rootDir)
            remoteUrl("https://github.com/smyrgeorge/${project.rootProject.name}/tree/main")
        }
    }
}
