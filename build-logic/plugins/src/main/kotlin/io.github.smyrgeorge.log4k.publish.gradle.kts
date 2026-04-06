import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("com.vanniktech.maven.publish")
}

val descriptions = mapOf(
    "log4k" to "A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.",
    "log4k-classic" to "A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.",
    "log4k-context" to "A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.",
    "log4k-slf4j" to "A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.",
)

configure<MavenPublishBaseExtension> {
    configure(
        KotlinMultiplatform(
            sourcesJar = SourcesJar.Sources()
        )
    )
    coordinates(
        groupId = project.group as String,
        artifactId = project.name,
        version = project.version as String
    )

    pom {
        name.set(project.name)
        description.set(descriptions[project.name] ?: error("Missing description for ${project.name}"))
        url.set("https://github.com/smyrgeorge/log4k")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://github.com/smyrgeorge/log4k/blob/main/LICENSE")
            }
        }

        developers {
            developer {
                id.set("smyrgeorge")
                name.set("Yorgos S.")
                email.set("smyrgoerge@gmail.com")
                url.set("https://smyrgeorge.github.io/")
            }
        }

        scm {
            url.set("https://github.com/smyrgeorge/log4k")
            connection.set("scm:git:https://github.com/smyrgeorge/log4k.git")
            developerConnection.set("scm:git:git@github.com:smyrgeorge/log4k.git")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}
