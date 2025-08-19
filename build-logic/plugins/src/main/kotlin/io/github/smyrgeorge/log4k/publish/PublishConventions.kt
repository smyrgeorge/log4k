package io.github.smyrgeorge.log4k.publish

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

@Suppress("unused")
class PublishConventions : Plugin<Project> {

    private val descriptions: Map<String, String> = mapOf(
        "log4k" to "A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.",
        "log4k-coroutines" to "A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.",
        "log4k-slf4j" to "A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.",
    )

    override fun apply(project: Project) {
        project.plugins.apply("com.vanniktech.maven.publish")
        project.extensions.configure<MavenPublishBaseExtension> {
            // sources publishing is always enabled by the Kotlin Multiplatform plugin
            configure(
                KotlinMultiplatform(
                    // whether to publish a sources jar
                    sourcesJar = true,
                    // configures the -javadoc artifact, possible values:
                    javadocJar = JavadocJar.Dokka("dokkaHtml"),
                )
            )
            coordinates(
                groupId = project.group as String,
                artifactId = project.name,
                version = project.version as String
            )

            pom {
                name.set(project.name)
                description.set(descriptions[project.name] ?: error("Missing description for $project.name"))
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

            // Configure publishing to Maven Central
            publishToMavenCentral()

            // Enable GPG signing for all publications
            signAllPublications()
        }
    }
}
