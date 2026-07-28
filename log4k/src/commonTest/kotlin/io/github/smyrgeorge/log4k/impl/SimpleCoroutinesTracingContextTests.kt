package io.github.smyrgeorge.log4k.impl

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.TracingContext
import kotlin.test.Test

/**
 * Tests for [SimpleCoroutinesTracingContext] as a value: identity semantics and rendering.
 * The span-slot behavior (atomic `restoreCurrent`, sibling isolation) is covered by the
 * tracer/context integration tests.
 */
class SimpleCoroutinesTracingContextTests {

    @Test
    fun toString_rendersBothSlots_withBalancedParentheses() {
        val ctx = TracingContext.create()
        assertThat(ctx.toString()).isEqualTo("TracingContext(current=null, parent=null)")
    }

    @Test
    fun contexts_useIdentitySemantics_notValueEquality() {
        // Two contexts built from the same (tracer, parent) are distinct carriers of distinct
        // `current` slots and must not compare equal (the class is deliberately not a data class).
        val a = TracingContext.create()
        val b = TracingContext.create()
        assertThat(a).isNotEqualTo(b)
        assertThat(a).isSameInstanceAs(a)
    }

    @Test
    fun currentSlots_areIndependentBetweenContexts() {
        val tracer = SimpleTracer("test.context.tracer", Level.TRACE)
        val a = TracingContext.create(tracer)
        val b = TracingContext.create(tracer)

        a.current = tracer.span("only-in-a")

        assertThat(a.current().name).isEqualTo("only-in-a")
        assertThat(b.currentOrNull()).isNull()
    }
}
