package io.github.smyrgeorge.log4k.annotation

/**
 * Opts out of [Trace] instrumentation.
 *
 * - On a **function**: excludes it from tracing, overriding both a class-level [Trace] and any
 *   `@Trace` on the function itself.
 * - On a **class**: disables tracing for *all* of its functions — including any that carry their
 *   own `@Trace` — so it acts as a per-class kill switch.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class NoTrace
