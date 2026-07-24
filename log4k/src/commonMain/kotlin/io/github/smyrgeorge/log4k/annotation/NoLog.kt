package io.github.smyrgeorge.log4k.annotation

/**
 * Opts out of [Logged] instrumentation.
 *
 * - On a **function**: excludes it from entry/exit logging, overriding both a class-level [Logged]
 *   and any `@Logged` on the function itself.
 * - On a **class**: disables logging for *all* of its functions — including any that carry their
 *   own `@Logged` — so it acts as a per-class kill switch.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class NoLog
