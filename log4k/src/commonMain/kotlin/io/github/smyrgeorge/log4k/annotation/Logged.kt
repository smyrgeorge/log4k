package io.github.smyrgeorge.log4k.annotation

import io.github.smyrgeorge.log4k.Level

/**
 * Marks a function — or a whole class (see below) — to be automatically instrumented with
 * entry/exit logging by the `log4k-compiler-plugin` Kotlin IR compiler plugin.
 *
 * At compile time, the body of an annotated function is wrapped so that it:
 * - logs `"→ name(args)"` on entry,
 * - logs `"← name = result (duration)"` on normal completion,
 * - logs `"✗ name failed (duration)"` at [Level.ERROR] — with the throwable attached — if the body
 *   throws, and then rethrows.
 *
 * The logs are emitted through a `log4k` [io.github.smyrgeorge.log4k.Logger] via the inline
 * [io.github.smyrgeorge.log4k.Logger.logged] helper (which calls
 * [io.github.smyrgeorge.log4k.Logger.log] directly), so both regular and `suspend` functions are
 * supported.
 *
 * The [io.github.smyrgeorge.log4k.Logger] is taken from a property named `log` (of type
 * [io.github.smyrgeorge.log4k.Logger]) declared in the enclosing class. If the class does not declare
 * one — or declares a `log` of a foreign type, such as `org.slf4j.Logger` — the plugin synthesizes
 * `private val _log_ = Logger.of(this::class)` (a distinct name, so it never clashes with the
 * foreign `log`).
 *
 * ```kotlin
 * class UserService {
 *     // reused by the plugin; if omitted, one is generated automatically.
 *     private val log = Logger.of(this::class)
 *
 *     @Logged
 *     fun compute(x: Int): Int = x * x
 * }
 * ```
 *
 * The logs are correlated with a span when one is in scope, resolved in this order:
 * 1. a [io.github.smyrgeorge.log4k.TracingContext] **context parameter or receiver** — its current
 *    span (`currentOrNull()`) is attached;
 * 2. otherwise a [io.github.smyrgeorge.log4k.TracingEvent.Span] in scope (e.g. a `Span.Local`
 *    receiver or context parameter) is attached directly;
 * 3. otherwise no span is attached.
 *
 * ```kotlin
 * @Logged(level = Level.DEBUG)
 * context(_: TracingContext)
 * suspend fun loadUser(id: Long): User { /* logs carry the current span id */ }
 *
 * // A span already in scope is used directly:
 * @Logged
 * context(_: TracingEvent.Span.Local)
 * fun audit(action: String) { /* logs carry this span */ }
 * ```
 *
 * The annotation may also be placed on a **class**. Then every eligible member function is
 * instrumented: one that is `public`, concrete (has a body), not a constructor, property accessor,
 * or inherited (fake-override) member. A function's own `@Logged` overrides the class-level
 * defaults (e.g. its [level]).
 *
 * @property level The [Level] used for the entry/exit log lines. Defaults to [Level.INFO]. The
 *   failure log line is always emitted at [Level.ERROR].
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Logged(
    val level: Level = Level.INFO,
)
