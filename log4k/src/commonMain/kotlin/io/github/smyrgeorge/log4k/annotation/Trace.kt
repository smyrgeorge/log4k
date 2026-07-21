package io.github.smyrgeorge.log4k.annotation

/**
 * Marks a function — or a whole class (see below) — to be automatically instrumented with a
 * tracing span by the `log4k-compiler-plugin` Kotlin IR compiler plugin.
 *
 * At compile time, the body of an annotated function is wrapped in a call to
 * [io.github.smyrgeorge.log4k.TracingContext.span], creating a new
 * [io.github.smyrgeorge.log4k.TracingEvent.Span] that is automatically started, ended, and — if
 * the body throws — marked as failed (the exception is recorded on the span and rethrown).
 *
 * The [io.github.smyrgeorge.log4k.TracingContext] used to create the span is taken from a
 * **context parameter** of the annotated function (of type [io.github.smyrgeorge.log4k.TracingContext]
 * or a subtype). A directly `@Trace`-annotated function that lacks such a parameter is a compile
 * error. For example:
 *
 * ```kotlin
 * context(_: TracingContext)
 * @Trace(name = "load-user")
 * suspend fun loadUser(id: Long): User {
 *     // original body — runs inside a "load-user" span.
 * }
 * ```
 *
 * Because the wrapping uses the `inline` [io.github.smyrgeorge.log4k.TracingContext.span]
 * helper, both regular and `suspend` functions are supported.
 *
 * Static tags can be attached to the span via [tags]:
 *
 * ```kotlin
 * context(_: TracingContext)
 * @Trace(name = "load-user", tags = [Tag("component", "billing")])
 * suspend fun loadUser(id: Long): User { /* ... */ }
 * ```
 *
 * The annotation may also be placed on a **class**. Then every eligible member function is
 * instrumented: one that is `public`, concrete (has a body), not a constructor, property accessor,
 * or inherited (fake-override) member, and that declares a [io.github.smyrgeorge.log4k.TracingContext]
 * context parameter. Functions that don't qualify are skipped silently — a class-level `@Trace`
 * never triggers the missing-context-parameter error. Individual functions can opt out with
 * [NoTrace], a function's own `@Trace` overrides the class-level defaults (e.g. its [name]), and
 * class-level [tags] are added to every generated span.
 *
 * ```kotlin
 * @Trace(tags = [Tag("layer", "service")])
 * class UserService {
 *     context(_: TracingContext) suspend fun load(id: Long): User { /* traced */ }
 *     @NoTrace context(_: TracingContext) suspend fun internal() { /* not traced */ }
 * }
 * ```
 *
 * @property name The span name. When left blank, it defaults to `ClassName.functionName` (or just
 *   the function name for a top-level function). Ignored when `@Trace` is placed on a class.
 * @property tags Static key/value tags added to the span. (Annotation parameters cannot be a
 *   `Map`, so tags are expressed as an array of [Tag] and materialized onto the span's `tags`
 *   at compile time.)
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Trace(
    val name: String = "",
    val tags: Array<Tag> = [],
)
