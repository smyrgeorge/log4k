package io.github.smyrgeorge.log4k.annotation

/**
 * Marks a function — or a whole class (see below) — to be automatically instrumented with metrics by
 * the `log4k-compiler-plugin` Kotlin IR compiler plugin (the classic Micrometer/Dropwizard `@Timed`).
 *
 * At compile time, the body of an annotated function is wrapped so that each invocation records, via
 * [io.github.smyrgeorge.log4k.Meter.timed], three metrics keyed off [name]:
 * - `"<name>.calls"` — a counter incremented on every invocation,
 * - `"<name>.errors"` — a counter incremented when the body throws,
 * - `"<name>.duration"` — a histogram of the invocation duration in milliseconds.
 *
 * The [io.github.smyrgeorge.log4k.Meter] is taken from a `meter: Meter` property declared in the
 * enclosing class; if the class does not declare one, the plugin synthesizes
 * `private val _meter_ = Meter.of(this::class)`. The three instruments are created once and cached
 * (see [io.github.smyrgeorge.log4k.Meter.Timed]). Because the wrapping uses the `inline`
 * [io.github.smyrgeorge.log4k.Meter.Timed.measure] helper, both regular and `suspend` functions are
 * supported.
 *
 * ```kotlin
 * class UserService {
 *     @Timed
 *     suspend fun loadUser(id: Long): User { /* recorded under "UserService.loadUser.*" */ }
 * }
 * ```
 *
 * The annotation may also be placed on a **class**. Then every eligible member function is
 * instrumented: one that is `public`, concrete (has a body), not a constructor, property accessor, or
 * inherited (fake-override) member. A function's own `@Timed` overrides the class-level defaults (e.g.
 * its [name]).
 *
 * @property name The base metric name. When left blank, it defaults to `ClassName.functionName` (or
 *   just the function name for a top-level function).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Timed(
    val name: String = "",
)
