package io.github.smyrgeorge.log4k.impl.extensions

private const val DELIM = "{}"
private const val ESCAPE = '\\'

/**
 * Substitutes every `{}` placeholder in this message pattern with the corresponding entry of [args].
 *
 * The rules mirror SLF4J's `MessageFormatter`, so patterns behave identically whether they arrive through the
 * log4k API or through the `log4k-slf4j` bridge:
 *
 * - `{}` is replaced by the next argument, left to right. `null` renders as `"null"`.
 * - `\{}` is an **escaped** placeholder: it renders as the literal `{}` and consumes no argument.
 * - `\\{}` is an escaped *escape*: one backslash is emitted and the placeholder is substituted normally.
 * - Placeholders in excess of the arguments are left as literal `{}`; arguments in excess of the placeholders
 *   are ignored.
 * - An argument whose `toString()` throws renders as `<toString() failed>` (the same convention as
 *   [io.github.smyrgeorge.log4k.Logger.logged]'s result rendering). Substitution runs on the async
 *   appender coroutine, where a propagated exception would silently drop the *whole* log line —
 *   a misbehaving argument must only cost its own rendering.
 */
internal fun String.format(args: Array<out Any?>): String {
    if (args.isEmpty()) return this
    var j = indexOf(DELIM)
    if (j < 0) return this

    val sb = StringBuilder(length + 50)
    var i = 0      // index of the first character not yet copied to [sb]
    var used = 0   // number of arguments consumed so far

    while (true) {
        if (isEscapedDelimiter(j)) {
            if (isDoubleEscaped(j)) {
                // "\\{}" — drop one backslash, then substitute as usual.
                sb.appendRange(this, i, j - 1)
                sb.appendArgument(args[used++])
                i = j + 2
            } else {
                // "\{}" — emit a literal "{}" and keep the argument for the next placeholder.
                // Only "{" is appended here; the "}" is picked up by the next copied range.
                sb.appendRange(this, i, j - 1)
                sb.append('{')
                i = j + 1
            }
        } else {
            sb.appendRange(this, i, j)
            sb.appendArgument(args[used++])
            i = j + 2
        }
        if (used == args.size) break // All arguments consumed; the tail is copied below.
        j = indexOf(DELIM, i)
        if (j < 0) break // No more placeholders; the tail is copied below.
    }

    sb.appendRange(this, i, length)
    return sb.toString()
}

/** Appends [arg] rendered defensively: a throwing `toString()` renders as `<toString() failed>`. */
private fun StringBuilder.appendArgument(arg: Any?) {
    append(runCatching { arg.toString() }.getOrElse { "<toString() failed>" })
}

/** True when the `{}` starting at [delimiterStart] is directly preceded by an escape character. */
private fun String.isEscapedDelimiter(delimiterStart: Int): Boolean =
    delimiterStart != 0 && this[delimiterStart - 1] == ESCAPE

/** True when the escape character preceding the `{}` at [delimiterStart] is itself escaped. */
private fun String.isDoubleEscaped(delimiterStart: Int): Boolean =
    delimiterStart >= 2 && this[delimiterStart - 2] == ESCAPE
