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
 * Static [tags] are attached to every recorded value as **metric dimensions** (labels):
 *
 * ```kotlin
 * @Timed(name = "orders.place", tags = [Tag("tier", "gold")])
 * fun placeOrder(id: Long): Order { /* "orders.place.*" values carry tier=gold */ }
 * ```
 *
 * Keep [tags] **static and low-cardinality**: they become time-series labels, and high-cardinality
 * dimensions (per-request ids, timestamps, …) blow up a metrics backend. For per-invocation data,
 * prefer a span attribute (`@Traced`) over a metric dimension.
 *
 * The annotation may also be placed on a **class**. Then every eligible member function is
 * instrumented: one that is `public`, concrete (has a body), not a constructor, property accessor, or
 * inherited (fake-override) member. Individual functions can opt out with [NoTime], a function's own
 * `@Timed` overrides the class-level defaults (e.g. its [name]), and class-level [tags] are added to
 * every instrumented member (a function's own tag with the same key wins).
 *
 * @property name The base metric name. When left blank, it defaults to `ClassName.functionName` (or
 *   just the function name for a top-level function).
 * @property tags Static key/value dimensions added to the recorded metrics. (Annotation parameters
 *   cannot be a `Map`, so tags are expressed as an array of [Tag].)
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Timed(
    val name: String = "",
    val tags: Array<Tag> = [],
)
