package io.github.smyrgeorge.log4k.annotation

/**
 * Opts out of [Timed] instrumentation.
 *
 * - On a **function**: excludes it from metrics, overriding both a class-level [Timed] and any
 *   `@Timed` on the function itself.
 * - On a **class**: disables metrics for *all* of its functions — including any that carry their
 *   own `@Timed` — so it acts as a per-class kill switch.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class NoTime
