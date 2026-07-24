package io.github.smyrgeorge.log4k.impl.registry

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** A minimal [Appender] test double named by a plain string. */
private class FakeAppender(override val name: String) : Appender<String> {
    override suspend fun append(event: String) = Unit
}

/** An [Appender] whose name is derived from its class, so it can be looked up by `KClass`. */
private class NamedAppender : Appender<String> {
    override val name: String = this::class.toName()
    override suspend fun append(event: String) = Unit
}

/**
 * Tests for [AppenderRegistry], which is a thin ordered list of appenders. Each test uses a fresh
 * registry, so there is no shared state to isolate.
 */
class AppenderRegistryTests {

    @Test
    fun newRegistry_isEmpty() {
        assertThat(AppenderRegistry<String>().all()).isEmpty()
    }

    @Test
    fun register_appendsInOrder_andAllReflectsThem() {
        val registry = AppenderRegistry<String>()
        val a = FakeAppender("a")
        val b = FakeAppender("b")

        registry.register(a)
        registry.register(b)

        assertThat(registry.all()).containsExactly(a, b) // insertion order preserved
    }

    @Test
    fun all_returnsDefensiveSnapshot() {
        val registry = AppenderRegistry<String>()
        registry.register(FakeAppender("a"))

        val snapshot = registry.all()
        registry.register(FakeAppender("b")) // must not affect the already-returned list

        assertThat(snapshot).hasSize(1)
    }

    @Test
    fun getByName_returnsRegisteredAppender() {
        val registry = AppenderRegistry<String>()
        val a = FakeAppender("a")
        registry.register(a)

        assertThat(registry.get("a")).isSameInstanceAs(a)
    }

    @Test
    fun getByName_unknown_throws() {
        assertFailsWith<IllegalStateException> { AppenderRegistry<String>().get("missing") }
    }

    @Test
    fun getByClass_returnsAppenderNamedByThatClass() {
        val registry = AppenderRegistry<String>()
        val named = NamedAppender()
        registry.register(named)

        assertThat(registry.get(NamedAppender::class)).isSameInstanceAs(named)
    }

    @Test
    fun getByClass_unknown_throws() {
        assertFailsWith<IllegalStateException> { AppenderRegistry<String>().get(NamedAppender::class) }
    }

    @Test
    fun unregisterByName_removesMatchingAndReturnsTrue() {
        val registry = AppenderRegistry<String>()
        registry.register(FakeAppender("a"))
        registry.register(FakeAppender("b"))

        assertThat(registry.unregister("a")).isTrue()
        assertThat(registry.all().map { it.name }).containsExactly("b")
    }

    @Test
    fun unregisterByName_noMatch_returnsFalse() {
        val registry = AppenderRegistry<String>()
        registry.register(FakeAppender("a"))

        assertThat(registry.unregister("missing")).isFalse()
        assertThat(registry.all()).hasSize(1)
    }

    @Test
    fun unregisterByAppender_removesByItsName() {
        val registry = AppenderRegistry<String>()
        val a = FakeAppender("a")
        registry.register(a)

        registry.unregister(a)

        assertThat(registry.all()).isEmpty()
    }

    @Test
    fun unregisterByClass_removesAppenderNamedByThatClass() {
        val registry = AppenderRegistry<String>()
        registry.register(NamedAppender())

        registry.unregister(NamedAppender::class)

        assertThat(registry.all()).isEmpty()
    }

    @Test
    fun sameName_registeredTwice_bothPresent_getReturnsFirst_unregisterRemovesAll() {
        val registry = AppenderRegistry<String>()
        val first = FakeAppender("dup")
        val second = FakeAppender("dup")
        registry.register(first)
        registry.register(second)

        assertThat(registry.all()).hasSize(2)
        assertThat(registry.get("dup")).isSameInstanceAs(first) // find() returns the first match

        assertThat(registry.unregister("dup")).isTrue()
        assertThat(registry.all()).isEmpty() // removeAll drops every appender with that name
    }

    @Test
    fun unregisterAll_clearsEverything() {
        val registry = AppenderRegistry<String>()
        registry.register(FakeAppender("a"))
        registry.register(FakeAppender("b"))

        registry.unregisterAll()

        assertThat(registry.all()).isEmpty()
    }
}
