package io.github.smyrgeorge.log4k.annotation

/**
 * Marks a function — or a whole class (see below) — to be automatically instrumented with a
 * tracing span by the `log4k-compiler-plugin` Kotlin IR compiler plugin.
 *
 * At compile time, the body of an annotated function is wrapped in a call to
 * [io.github.smyrgeorge.log4k.TracingContext.traced], creating a new
 * [io.github.smyrgeorge.log4k.TracingEvent.Span] that is automatically started, ended, and — if
 * the body throws — marked as failed (the exception is recorded on the span and rethrown).
 *
 * The new span's **parent** (and the tracer that creates it) is resolved from what is in scope, in
 * order:
 * 1. a [io.github.smyrgeorge.log4k.TracingContext] parameter/receiver — the span nests under its
 *    current span;
 * 2. otherwise a [io.github.smyrgeorge.log4k.TracingEvent.Span] parameter/receiver (e.g. a
 *    `Span.Local` receiver) — used directly as the parent;
 * 3. otherwise a `trace: Tracer` member of the enclosing class — reused, or synthesized as
 *    `private val _trace_ = Tracer.of(this::class)` — which creates a new **root** span.
 *
 * ```kotlin
 * @Traced(name = "load-user")
 * context(_: TracingContext)
 * suspend fun loadUser(id: Long): User {
 *     // original body — runs inside a "load-user" span, nested under the context's current span.
 * }
 * ```
 *
 * Because the wrapping uses the `inline` [io.github.smyrgeorge.log4k.TracingContext.traced]
 * helper, both regular and `suspend` functions are supported.
 *
 * Static tags can be attached to the span via [tags]:
 *
 * ```kotlin
 * context(_: TracingContext)
 * @Traced(name = "load-user", tags = [Tag("component", "billing")])
 * suspend fun loadUser(id: Long): User { /* ... */ }
 * ```
 *
 * The annotation may also be placed on a **class**. Then every eligible member function is
 * instrumented: one that is `public`, concrete (has a body), not a constructor, property accessor, or
 * inherited (fake-override) member. Members without a `TracingContext`/`Span` in scope fall back to
 * the class' tracer (case 3 above). Individual functions can opt out with [NoTrace], a function's own
 * `@Traced` overrides the class-level defaults (e.g. its [name]), and class-level [tags] are added to
 * every generated span.
 *
 * ```kotlin
 * @Traced(tags = [Tag("layer", "service")])
 * class UserService {
 *     context(_: TracingContext) suspend fun load(id: Long): User { /* traced */ }
 *     fun helper(): Int { /* traced as a root span via the synthesized `_trace_` */ }
 *     @NoTrace context(_: TracingContext) suspend fun internal() { /* not traced */ }
 * }
 * ```
 *
 * @property name The span name. When left blank, it defaults to `ClassName.functionName` (or just
 *   the function name for a top-level function). Ignored when `@Traced` is placed on a class.
 * @property tags Static key/value tags added to the span. (Annotation parameters cannot be a
 *   `Map`, so tags are expressed as an array of [Tag] and materialized onto the span's `tags`
 *   at compile time.)
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Traced(
    val name: String = "",
    val tags: Array<Tag> = [],
)
