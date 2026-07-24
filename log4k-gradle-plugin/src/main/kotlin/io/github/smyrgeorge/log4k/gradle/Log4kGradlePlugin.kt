package io.github.smyrgeorge.log4k.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Gradle plugin that wires the log4k Kotlin IR compiler plugin onto every Kotlin compilation.
 *
 * Consumers simply apply the plugin — exactly like any other Kotlin compiler plugin:
 *
 * ```kotlin
 * plugins {
 *     id("io.github.smyrgeorge.log4k") version "<version>"
 * }
 * ```
 *
 * There is nothing else to configure: `@Traced`, `@Timed` and `@Logged` instrumentation runs for
 * all targets and the common metadata compilation. The annotations themselves live in
 * `log4k-classic`, which the consumer is expected to add as a regular dependency.
 */
public class Log4kGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        // No extra configuration required — the compiler plugin has no options.
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    // Must match `Log4kCompilerPluginRegistrar.pluginId` in the compiler-plugin module.
    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = COMPILER_PLUGIN_GROUP,
        artifactId = COMPILER_PLUGIN_ARTIFACT,
        version = BuildConfig.VERSION,
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> =
        kotlinCompilation.target.project.provider { emptyList() }

    private companion object {
        const val COMPILER_PLUGIN_ID = "io.github.smyrgeorge.log4k.compiler"
        const val COMPILER_PLUGIN_GROUP = "io.github.smyrgeorge"
        const val COMPILER_PLUGIN_ARTIFACT = "log4k-compiler-plugin"
    }
}
