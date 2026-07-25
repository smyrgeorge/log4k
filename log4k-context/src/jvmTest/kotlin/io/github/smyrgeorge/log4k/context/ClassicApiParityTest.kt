package io.github.smyrgeorge.log4k.context

import assertk.assertThat
import assertk.assertions.isEmpty
import io.github.smyrgeorge.log4k.Logger
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.test.Test

/**
 * Enforces API parity between the `log4k-classic` extensions (`Logger.trace/…`) and the `Log4kClassic`
 * forwarders in this module (`log.classic.trace/…`). Adding, removing or re-typing a function on one
 * side without mirroring it on the other fails this test, naming the offending signatures.
 *
 * JVM-only: it reflects over the two compiled `*Kt` facade classes. Both facades are compared by
 * `(functionName, paramsAfterReceiver)`, which works because:
 * - Classic functions are `Logger.xxx` extensions — plain names, first param `Logger`.
 * - [Log4kClassic] is a `@JvmInline value class`, so its forwarders are **name-mangled**
 *   (`trace-<hash>`) with the receiver erased to `Logger`. Filtering the context facade by a `-` in the
 *   name isolates exactly those forwarders (the accessor `getClassic` and the context-aware overloads
 *   are not mangled), and stripping the `-<hash>` suffix + dropping the leading receiver normalizes
 *   both sides to the same shape.
 */
class ClassicApiParityTest {

    private data class Sig(val name: String, val params: List<Class<*>>) {
        override fun toString(): String = "$name(${params.joinToString { it.simpleName }})"
    }

    private fun Method.signature(): Sig = Sig(name.substringBefore('-'), parameterTypes.drop(1))

    private fun facadeMethods(fqName: String): List<Method> =
        Class.forName(fqName).declaredMethods.filter {
            Modifier.isPublic(it.modifiers) && Modifier.isStatic(it.modifiers) && !it.isSynthetic && '$' !in it.name
        }

    /** The public `Logger.xxx` classic extensions. */
    private fun classicSignatures(): Set<Sig> =
        facadeMethods("io.github.smyrgeorge.log4k.classic.ExtentionsKt")
            .filter { it.parameterTypes.firstOrNull() == Logger::class.java }
            .map { it.signature() }
            .toSet()

    /** The `Log4kClassic.xxx` forwarders — the mangled methods on the context facade. */
    private fun log4kClassicSignatures(): Set<Sig> =
        facadeMethods("io.github.smyrgeorge.log4k.context.ExtentionsKt")
            .filter { '-' in it.name } // value-class receiver -> mangled; excludes getClassic & context overloads
            .map { it.signature() }
            .toSet()

    @Test
    fun classicApi_isFullyMirrored_onLog4kClassic() {
        val classic = classicSignatures()
        val mirrored = log4kClassicSignatures()

        val missing = (classic - mirrored).map { it.toString() }.sorted()
        val extra = (mirrored - classic).map { it.toString() }.sorted()

        // Add the missing forwarders to `Log4kClassic` in log4k-context/.../context/extentions.kt.
        assertThat(missing, "classic functions not mirrored on Log4kClassic").isEmpty()
        // Remove these from `Log4kClassic` (or add the matching `log4k-classic` extension).
        assertThat(extra, "Log4kClassic forwarders with no classic counterpart").isEmpty()
    }
}
