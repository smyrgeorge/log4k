package io.github.smyrgeorge.log4k.annotation

/**
 * Masks a parameter's value in [Logged] instrumentation.
 *
 * The entry log line of a `@Logged` function renders every parameter as `name=value`. When a
 * parameter is annotated with `@Masked`, the literal `<MASKED>` is rendered instead of the real
 * value — the value is never `toString()`ed, so sensitive data (passwords, tokens, PII) cannot leak
 * into the logs:
 *
 * ```kotlin
 * @Logged
 * fun login(username: String, @Masked password: String): Boolean { /* ... */ }
 *
 * // login("alice", "hunter2") logs:
 * // → login(username=alice, password=<MASKED>)
 * ```
 *
 * Masking applies only to the parameter rendering of the entry line; the exit line's result value
 * is unaffected.
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class Masked
