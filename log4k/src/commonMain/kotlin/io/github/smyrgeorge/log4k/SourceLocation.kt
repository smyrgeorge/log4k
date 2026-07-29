package io.github.smyrgeorge.log4k

/**
 * A location in source code (file, line, function) — captured at **compile time** by the
 * `log4k-compiler-plugin` (the runtime never walks the stack).
 *
 * When the compiler plugin is applied, the emitted [LoggingEvent]s carry one (as
 * [LoggingEvent.callSite]):
 * - every call to a log4k logging entry point (`Logger.log`, the `at`/`atInfo` builder DSL, and the
 *   classic `Logger.info`/`warn`/… extensions) is rewritten to record its **call site**;
 * - the entry/exit/failure lines generated for a `@Logged` function record the **declaration** of
 *   the annotated function instead.
 *
 * Because the values are compile-time constants, accurate locations cost nothing at runtime — and
 * they work uniformly on every Kotlin target, including Native, JS, and Wasm, where walking the
 * stack is expensive or impossible. Without the plugin, [LoggingEvent.callSite] is simply `null`.
 *
 * @property file The simple file name (e.g. `UserService.kt`) — deliberately not the full path, so
 *   binaries do not leak build-machine directory layouts.
 * @property line The 1-based line number.
 * @property function The enclosing (or declared) function, as `ClassName.functionName` (or just the
 *   function name for a top-level function).
 */
data class SourceLocation(
    val file: String,
    val line: Int,
    val function: String,
) {
    override fun toString(): String = "$function($file:$line)"
}
