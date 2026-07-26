package io.github.smyrgeorge.log4k.impl.extensions

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test

/**
 * Tests for [String.format], the SLF4J-style `{}` placeholder substitution used by all
 * logging appenders. The expected values mirror SLF4J's `MessageFormatter` behavior, so the
 * `log4k-slf4j` bridge and the native API render patterns identically.
 */
class StringFormatTests {

    /** Calls the internal [format] with a `vararg` for readability. */
    private fun String.fmt(vararg args: Any?): String = format(args)

    // Fast paths: the receiver itself must be returned, not a copy.

    @Test
    fun noArguments_returnsSamePatternInstance() {
        val pattern = "Hello, {}!"
        assertThat(pattern.fmt()).isSameInstanceAs(pattern)
    }

    @Test
    fun noPlaceholders_returnsSamePatternInstance() {
        val pattern = "Hello, world!"
        assertThat(pattern.fmt("ignored")).isSameInstanceAs(pattern)
    }

    @Test
    fun emptyPattern_returnsSamePatternInstance() {
        val pattern = ""
        assertThat(pattern.fmt("ignored")).isSameInstanceAs(pattern)
    }

    @Test
    fun bracesWithoutPair_areNotPlaceholders() {
        val pattern = "indexed {0} and spaced { } stay put"
        assertThat(pattern.fmt("ignored")).isSameInstanceAs(pattern)
    }

    // Plain substitution.

    @Test
    fun singlePlaceholder_isSubstituted() {
        assertThat("Hello, {}!".fmt("world")).isEqualTo("Hello, world!")
    }

    @Test
    fun multiplePlaceholders_substituteLeftToRight() {
        assertThat("{} + {} = {}".fmt(1, 2, 3)).isEqualTo("1 + 2 = 3")
    }

    @Test
    fun placeholderAtStartAndEnd_isSubstituted() {
        assertThat("{} middle {}".fmt("a", "b")).isEqualTo("a middle b")
    }

    @Test
    fun adjacentPlaceholders_areSubstituted() {
        assertThat("{}{}{}".fmt("a", "b", "c")).isEqualTo("abc")
    }

    @Test
    fun patternConsistingOnlyOfPlaceholder_isSubstituted() {
        assertThat("{}".fmt("only")).isEqualTo("only")
    }

    @Test
    fun unicodeAroundPlaceholders_isPreserved() {
        assertThat("α {} ω 🚀{}".fmt(1, 2)).isEqualTo("α 1 ω 🚀2")
    }

    // Argument rendering.

    @Test
    fun nullArgument_rendersAsNullText() {
        assertThat("value: {}".fmt(null)).isEqualTo("value: null")
    }

    @Test
    fun nonStringArguments_renderViaToString() {
        assertThat("{} {} {}".fmt(42, true, listOf(1, 2))).isEqualTo("42 true [1, 2]")
    }

    @Test
    fun argumentContainingPlaceholder_isNotReprocessed() {
        assertThat("{} then {}".fmt("{}", "x")).isEqualTo("{} then x")
    }

    // Arity mismatches.

    @Test
    fun excessArguments_areIgnored() {
        assertThat("only {}".fmt("a", "b", "c")).isEqualTo("only a")
    }

    @Test
    fun excessPlaceholders_remainLiteral() {
        assertThat("{} and {} and {}".fmt("a")).isEqualTo("a and {} and {}")
    }

    // Escaping, mirroring SLF4J's MessageFormatter.

    @Test
    fun escapedPlaceholder_rendersLiterallyAndConsumesNoArgument() {
        assertThat("Set \\{} to {}".fmt("x")).isEqualTo("Set {} to x")
    }

    @Test
    fun escapedPlaceholderAtStart_rendersLiterally() {
        assertThat("\\{} {} {}".fmt("a", "b")).isEqualTo("{} a b")
    }

    @Test
    fun doubleEscapedPlaceholder_emitsBackslashAndSubstitutes() {
        assertThat("dir: \\\\{}!".fmt("x")).isEqualTo("dir: \\x!")
    }

    @Test
    fun tripleBackslash_behavesAsDoubleEscape() {
        // SLF4J only inspects the two characters before "{}", so "\\\{}" keeps
        // two backslashes and substitutes the argument.
        assertThat("\\\\\\{}".fmt("x")).isEqualTo("\\\\x")
    }

    @Test
    fun backslashNotBeforePlaceholder_isLiteral() {
        assertThat("a\\b {}".fmt("x")).isEqualTo("a\\b x")
    }

    @Test
    fun argumentsExhaustedBeforeEscapedPlaceholder_keepTailVerbatim() {
        // Once every argument is consumed the rest of the pattern is copied as-is,
        // so the escape backslash survives — exactly like SLF4J.
        assertThat("{} \\{}".fmt("a")).isEqualTo("a \\{}")
    }
}
