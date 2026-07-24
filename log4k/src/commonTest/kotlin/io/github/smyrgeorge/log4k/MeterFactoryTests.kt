package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.github.smyrgeorge.log4k.impl.SimpleMeter
import io.github.smyrgeorge.log4k.impl.SimpleMeterFactory
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.test.Test

/**
 * Tests for [MeterFactory] (and its [SimpleMeterFactory] implementation). The factory contract:
 * `get(name)` returns the cached instance from the shared [Meter.registry], or creates one via the
 * abstract `create`, registers it, and returns it; `get(clazz)` delegates to `get(clazz.toName())`.
 *
 * The registry is global and never evicts, so every test uses a unique name to stay isolated, and any
 * global state it flips ([Meter.factory], the registry mute set) is restored in a `finally`.
 */
class MeterFactoryTests {

    private class MeterFactorySample

    @Test
    fun get_byName_createsSimpleMeterWithThatName() {
        val meter = SimpleMeterFactory().get("meter.factory.byName")
        assertThat(meter).isInstanceOf(SimpleMeter::class)
        assertThat(meter.name).isEqualTo("meter.factory.byName")
    }

    @Test
    fun get_sameName_returnsCachedInstance() {
        val factory = SimpleMeterFactory()
        assertThat(factory.get("meter.factory.cached")).isSameInstanceAs(factory.get("meter.factory.cached"))
    }

    @Test
    fun get_differentNames_returnDistinctInstances() {
        val factory = SimpleMeterFactory()
        assertThat(factory.get("meter.factory.a")).isNotSameInstanceAs(factory.get("meter.factory.b"))
    }

    @Test
    fun get_cachesAcrossFactoryInstances_viaSharedRegistry() {
        val a = SimpleMeterFactory().get("meter.factory.shared")
        val b = SimpleMeterFactory().get("meter.factory.shared")
        assertThat(a).isSameInstanceAs(b)
    }

    @Test
    fun get_registersCreatedInstanceInRegistry() {
        val meter = SimpleMeterFactory().get("meter.factory.registered")
        assertThat(Meter.registry.get("meter.factory.registered")).isSameInstanceAs(meter)
    }

    @Test
    fun create_doesNotRegister() {
        val meter = SimpleMeterFactory().create("meter.factory.noregister")
        assertThat(meter.name).isEqualTo("meter.factory.noregister")
        assertThat(Meter.registry.get("meter.factory.noregister")).isNull()
    }

    @Test
    fun get_byClass_matchesToNameAndCaches() {
        val factory = SimpleMeterFactory()
        val byClass = factory.get(MeterFactorySample::class)
        assertThat(byClass.name).isEqualTo(MeterFactorySample::class.toName())
        assertThat(factory.get(MeterFactorySample::class)).isSameInstanceAs(byClass)
        assertThat(factory.get(MeterFactorySample::class.toName())).isSameInstanceAs(byClass)
    }

    @Test
    fun get_invokesCreateOnlyOncePerName() {
        var createCount = 0
        val factory = object : MeterFactory() {
            override fun create(name: String): Meter {
                createCount++
                return SimpleMeter(name, Level.INFO)
            }
        }
        val a = factory.get("meter.factory.count.same")
        val b = factory.get("meter.factory.count.same")
        assertThat(createCount).isEqualTo(1)
        assertThat(a).isSameInstanceAs(b)

        factory.get("meter.factory.count.other")
        assertThat(createCount).isEqualTo(2)
    }

    @Test
    fun simpleFactory_create_usesCurrentRootLoggerLevel() {
        val saved = RootLogger.Metering.level
        try {
            RootLogger.Metering.level = Level.WARN
            assertThat(SimpleMeterFactory().create("meter.factory.level.warn").level).isEqualTo(Level.WARN)
            RootLogger.Metering.level = Level.ERROR
            assertThat(SimpleMeterFactory().create("meter.factory.level.error").level).isEqualTo(Level.ERROR)
        } finally {
            RootLogger.Metering.level = saved
        }
    }

    @Test
    fun get_forPreMutedName_returnsMutedInstance() {
        val name = "meter.factory.premuted"
        Meter.registry.mute(name)
        try {
            val meter = SimpleMeterFactory().get(name)
            assertThat(meter.isMuted()).isTrue()
        } finally {
            Meter.registry.unmute(name)
        }
    }

    @Test
    fun of_usesConfiguredFactory() {
        val original = Meter.factory
        var created = false
        try {
            Meter.factory = object : MeterFactory() {
                override fun create(name: String): Meter {
                    created = true
                    return SimpleMeter(name, Level.INFO)
                }
            }
            val meter = Meter.of("meter.factory.of.custom")
            assertThat(created).isTrue()
            assertThat(meter.name).isEqualTo("meter.factory.of.custom")
        } finally {
            Meter.factory = original
        }
    }
}
