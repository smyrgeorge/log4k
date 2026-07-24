package io.github.smyrgeorge.log4k

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.github.smyrgeorge.log4k.impl.SimpleLogger
import io.github.smyrgeorge.log4k.impl.SimpleLoggerFactory
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.test.Test

/**
 * Tests for [LoggerFactory] (and its [SimpleLoggerFactory] implementation). The factory contract:
 * `get(name)` returns the cached instance from the shared [Logger.registry], or creates one via the
 * abstract `create`, registers it, and returns it; `get(clazz)` delegates to `get(clazz.toName())`.
 *
 * The registry is global and never evicts, so every test uses a unique name to stay isolated, and any
 * global state it flips ([Logger.factory], the registry mute set) is restored in a `finally`.
 */
class LoggerFactoryTests {

    private class LoggerFactorySample

    @Test
    fun get_byName_createsSimpleLoggerWithThatName() {
        val logger = SimpleLoggerFactory().get("logger.factory.byName")
        assertThat(logger).isInstanceOf(SimpleLogger::class)
        assertThat(logger.name).isEqualTo("logger.factory.byName")
    }

    @Test
    fun get_sameName_returnsCachedInstance() {
        val factory = SimpleLoggerFactory()
        assertThat(factory.get("logger.factory.cached")).isSameInstanceAs(factory.get("logger.factory.cached"))
    }

    @Test
    fun get_differentNames_returnDistinctInstances() {
        val factory = SimpleLoggerFactory()
        assertThat(factory.get("logger.factory.a")).isNotSameInstanceAs(factory.get("logger.factory.b"))
    }

    @Test
    fun get_cachesAcrossFactoryInstances_viaSharedRegistry() {
        val a = SimpleLoggerFactory().get("logger.factory.shared")
        val b = SimpleLoggerFactory().get("logger.factory.shared")
        assertThat(a).isSameInstanceAs(b)
    }

    @Test
    fun get_registersCreatedInstanceInRegistry() {
        val logger = SimpleLoggerFactory().get("logger.factory.registered")
        assertThat(Logger.registry.get("logger.factory.registered")).isSameInstanceAs(logger)
    }

    @Test
    fun create_doesNotRegister() {
        val logger = SimpleLoggerFactory().create("logger.factory.noregister")
        assertThat(logger.name).isEqualTo("logger.factory.noregister")
        assertThat(Logger.registry.get("logger.factory.noregister")).isNull()
    }

    @Test
    fun get_byClass_matchesToNameAndCaches() {
        val factory = SimpleLoggerFactory()
        val byClass = factory.get(LoggerFactorySample::class)
        assertThat(byClass.name).isEqualTo(LoggerFactorySample::class.toName())
        assertThat(factory.get(LoggerFactorySample::class)).isSameInstanceAs(byClass)
        assertThat(factory.get(LoggerFactorySample::class.toName())).isSameInstanceAs(byClass)
    }

    @Test
    fun get_invokesCreateOnlyOncePerName() {
        var createCount = 0
        val factory = object : LoggerFactory() {
            override fun create(name: String): Logger {
                createCount++
                return SimpleLogger(name, Level.INFO)
            }
        }
        val a = factory.get("logger.factory.count.same")
        val b = factory.get("logger.factory.count.same")
        assertThat(createCount).isEqualTo(1)
        assertThat(a).isSameInstanceAs(b)

        factory.get("logger.factory.count.other")
        assertThat(createCount).isEqualTo(2)
    }

    @Test
    fun simpleFactory_create_usesCurrentRootLoggerLevel() {
        val saved = RootLogger.Logging.level
        try {
            RootLogger.Logging.level = Level.WARN
            assertThat(SimpleLoggerFactory().create("logger.factory.level.warn").level).isEqualTo(Level.WARN)
            RootLogger.Logging.level = Level.ERROR
            assertThat(SimpleLoggerFactory().create("logger.factory.level.error").level).isEqualTo(Level.ERROR)
        } finally {
            RootLogger.Logging.level = saved
        }
    }

    @Test
    fun get_forPreMutedName_returnsMutedInstance() {
        val name = "logger.factory.premuted"
        Logger.registry.mute(name)
        try {
            val logger = SimpleLoggerFactory().get(name)
            assertThat(logger.isMuted()).isTrue()
        } finally {
            Logger.registry.unmute(name)
        }
    }

    @Test
    fun of_usesConfiguredFactory() {
        val original = Logger.factory
        var created = false
        try {
            Logger.factory = object : LoggerFactory() {
                override fun create(name: String): Logger {
                    created = true
                    return SimpleLogger(name, Level.INFO)
                }
            }
            val logger = Logger.of("logger.factory.of.custom")
            assertThat(created).isTrue()
            assertThat(logger.name).isEqualTo("logger.factory.of.custom")
        } finally {
            Logger.factory = original
        }
    }
}
