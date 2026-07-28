import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("com.vanniktech.maven.publish")
}

val descriptions = mapOf(
    "log4k" to "A Comprehensive Logging and Tracing Solution for Kotlin Multiplatform.",
    "log4k-classic" to "Classic logging extensions for log4k: trace/debug/info/warn/error with varargs, throwables and lazy message lambdas.",
    "log4k-compiler-plugin" to "Kotlin compiler plugin for log4k: compile-time @Logged, @Timed and @Traced instrumentation.",
    "log4k-gradle-plugin" to "Gradle plugin that wires the log4k Kotlin compiler plugin onto every Kotlin compilation.",
    "log4k-context" to "Context-parameter logging extensions for log4k: log lines automatically correlate with the TracingContext or Span in scope.",
    "log4k-integrations" to "log4k appenders integrating with third-party observability services: Datadog Agent and OTLP trace publishing.",
    "log4k-slf4j" to "SLF4J 2.x provider backed by log4k: routes every org.slf4j.Logger call into log4k's logging pipeline.",
    "log4k-slf4j-appender" to "log4k appender that forwards logging events to SLF4J (Logback, Log4j2, ...).",
)

configure<MavenPublishBaseExtension> {
    // Gradle plugin modules publish the plugin jar plus its marker; everything else is Kotlin
    // Multiplatform (JVM-only modules are still applied via the `multiplatform` Kotlin plugin).
    if (pluginManager.hasPlugin("java-gradle-plugin")) {
        configure(
            GradlePlugin(
                javadocJar = JavadocJar.Empty(),
                sourcesJar = SourcesJar.Sources()
            )
        )
    } else {
        configure(
            KotlinMultiplatform(
                sourcesJar = SourcesJar.Sources()
            )
        )
    }
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
