package io.github.smyrgeorge.log4k.examples

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.classic.info
import io.github.smyrgeorge.log4k.impl.appenders.simple.SimpleJsonConsoleLoggingAppender
import io.github.smyrgeorge.log4k.impl.extensions.atInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.seconds

object CallSiteCompilerPlugin {

    private val log = Logger.of(this::class)

    // The plugin rewrites every log call below at compile time, so each emitted event carries the
    // exact file/line/function it came from — no runtime stack-walking, on every Kotlin target
    // (JVM, Native, JS, Wasm). The JSON appender surfaces it as the `caller_method_name` /
    // `caller_file_name` / `caller_line_number` fields (the plain-text console appender keeps its
    // lines free of it).

    private fun eagerClassic() {
        log.info("classic call -> carries CallSiteCompilerPlugin.kt + this line")
    }

    private fun lazyClassic() {
        // Still lazy: the lambda is neither allocated nor invoked when the level is disabled.
        log.info { "lazy classic call -> also carries its call site" }
    }

    private fun builderDsl() {
        log.atInfo { message = "builder DSL call -> also carries its call site" }
    }

    fun run() = runBlocking {
        RootLogger.Logging.level = Level.INFO

        // Start from a clean slate so each line is printed exactly once; the JSON appender shows
        // the injected call site as `caller_*` fields.
        RootLogger.Logging.appenders.unregisterAll()
        RootLogger.Logging.appenders.register(SimpleJsonConsoleLoggingAppender())

        eagerClassic()
        lazyClassic()
        builderDsl()

        // Give the async logging appender time to flush.
        delay(1.seconds)
    }
}
