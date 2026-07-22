package io.github.smyrgeorge.log4k.compiler

import io.github.smyrgeorge.log4k.compiler.logged.LoggedIrGenerationExtension
import io.github.smyrgeorge.log4k.compiler.timed.TimedIrGenerationExtension
import io.github.smyrgeorge.log4k.compiler.trace.TraceIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Registers every log4k IR instrumentation pass:
 * - [TraceIrGenerationExtension] — wraps `@Trace` functions in a tracing span.
 * - [TimedIrGenerationExtension] — wraps `@Timed` functions in call/error/duration metrics.
 * - [LoggedIrGenerationExtension] — wraps `@Logged` functions in entry/exit logging.
 */
@OptIn(ExperimentalCompilerApi::class)
class Log4kCompilerPluginRegistrar : CompilerPluginRegistrar() {
    override val pluginId: String = "io.github.smyrgeorge.log4k.compiler"
    override val supportsK2: Boolean = true

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        IrGenerationExtension.registerExtension(TraceIrGenerationExtension(configuration))
        IrGenerationExtension.registerExtension(TimedIrGenerationExtension(configuration))
        IrGenerationExtension.registerExtension(LoggedIrGenerationExtension(configuration))
    }
}
