package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.github.smyrgeorge.log4k.impl.SimpleTracer
import io.github.smyrgeorge.log4k.impl.SimpleTracerFactory
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.test.Test

/**
 * Tests for [TracerFactory] (and its [SimpleTracerFactory] implementation). The factory contract:
 * `get(name)` returns the cached instance from the shared [Tracer.registry], or creates one via the
 * abstract `create`, registers it, and returns it; `get(clazz)` delegates to `get(clazz.toName())`.
 *
 * The registry is global and never evicts, so every test uses a unique name to stay isolated, and any
 * global state it flips ([Tracer.factory], the registry mute set) is restored in a `finally`.
 */
class TracerFactoryTests {

    private class TracerFactorySample

    @Test
    fun get_byName_createsSimpleTracerWithThatName() {
        val tracer = SimpleTracerFactory().get("tracer.factory.byName")
        assertThat(tracer).isInstanceOf(SimpleTracer::class)
        assertThat(tracer.name).isEqualTo("tracer.factory.byName")
    }

    @Test
    fun get_sameName_returnsCachedInstance() {
        val factory = SimpleTracerFactory()
        assertThat(factory.get("tracer.factory.cached")).isSameInstanceAs(factory.get("tracer.factory.cached"))
    }

    @Test
    fun get_differentNames_returnDistinctInstances() {
        val factory = SimpleTracerFactory()
        assertThat(factory.get("tracer.factory.a")).isNotSameInstanceAs(factory.get("tracer.factory.b"))
    }

    @Test
    fun get_cachesAcrossFactoryInstances_viaSharedRegistry() {
        val a = SimpleTracerFactory().get("tracer.factory.shared")
        val b = SimpleTracerFactory().get("tracer.factory.shared")
        assertThat(a).isSameInstanceAs(b)
    }

    @Test
    fun get_registersCreatedInstanceInRegistry() {
        val tracer = SimpleTracerFactory().get("tracer.factory.registered")
        assertThat(Tracer.registry.get("tracer.factory.registered")).isSameInstanceAs(tracer)
    }

    @Test
    fun create_doesNotRegister() {
        val tracer = SimpleTracerFactory().create("tracer.factory.noregister")
        assertThat(tracer.name).isEqualTo("tracer.factory.noregister")
        assertThat(Tracer.registry.get("tracer.factory.noregister")).isNull()
    }

    @Test
    fun get_byClass_matchesToNameAndCaches() {
        val factory = SimpleTracerFactory()
        val byClass = factory.get(TracerFactorySample::class)
        assertThat(byClass.name).isEqualTo(TracerFactorySample::class.toName())
        assertThat(factory.get(TracerFactorySample::class)).isSameInstanceAs(byClass)
        assertThat(factory.get(TracerFactorySample::class.toName())).isSameInstanceAs(byClass)
    }

    @Test
    fun get_invokesCreateOnlyOncePerName() {
        var createCount = 0
        val factory = object : TracerFactory() {
            override fun create(name: String): Tracer {
                createCount++
                return SimpleTracer(name, Level.INFO)
            }
        }
        val a = factory.get("tracer.factory.count.same")
        val b = factory.get("tracer.factory.count.same")
        assertThat(createCount).isEqualTo(1)
        assertThat(a).isSameInstanceAs(b)

        factory.get("tracer.factory.count.other")
        assertThat(createCount).isEqualTo(2)
    }

    @Test
    fun simpleFactory_create_usesCurrentRootLoggerLevel() {
        val saved = RootLogger.Tracing.level
        try {
            RootLogger.Tracing.level = Level.WARN
            assertThat(SimpleTracerFactory().create("tracer.factory.level.warn").level).isEqualTo(Level.WARN)
            RootLogger.Tracing.level = Level.ERROR
            assertThat(SimpleTracerFactory().create("tracer.factory.level.error").level).isEqualTo(Level.ERROR)
        } finally {
            RootLogger.Tracing.level = saved
        }
    }

    @Test
    fun get_forPreMutedName_returnsMutedInstance() {
        val name = "tracer.factory.premuted"
        Tracer.registry.mute(name)
        try {
            val tracer = SimpleTracerFactory().get(name)
            assertThat(tracer.isMuted()).isTrue()
        } finally {
            Tracer.registry.unmute(name)
        }
    }

    @Test
    fun of_usesConfiguredFactory() {
        val original = Tracer.factory
        var created = false
        try {
            Tracer.factory = object : TracerFactory() {
                override fun create(name: String): Tracer {
                    created = true
                    return SimpleTracer(name, Level.INFO)
                }
            }
            val tracer = Tracer.of("tracer.factory.of.custom")
            assertThat(created).isTrue()
            assertThat(tracer.name).isEqualTo("tracer.factory.of.custom")
        } finally {
            Tracer.factory = original
        }
    }
}
