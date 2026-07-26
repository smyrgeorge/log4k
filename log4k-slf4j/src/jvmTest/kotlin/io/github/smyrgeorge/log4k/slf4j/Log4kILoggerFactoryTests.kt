package io.github.smyrgeorge.log4k.slf4j

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.RootLogger
import kotlin.test.AfterTest
import kotlin.test.Test
import io.github.smyrgeorge.log4k.Logger as CoreLogger

/**
 * Tests for [Log4kILoggerFactory] — the part of the bridge that makes SLF4J logger names resolve through
 * log4k's own [CoreLogger.factory], so that a logger acquired through SLF4J and one acquired through the
 * log4k API are backed by the same registry entry.
 */
class Log4kILoggerFactoryTests {

    private val factory = Log4kILoggerFactory()

    private var savedRootLevel: Level = RootLogger.Logging.level

    @AfterTest
    fun teardown() {
        RootLogger.Logging.level = savedRootLevel
    }

    @Test
    fun getLogger_returnsALog4kBackedSlf4jLogger() {
        val logger = factory.getLogger("factory.type")

        assertThat(logger).isInstanceOf(Log4kLogger::class)
        assertThat(logger.name).isEqualTo("factory.type")
    }

    @Test
    fun getLogger_sharesTheRegistryWithTheCoreApi() {
        val name = "factory.shared.registry"

        val viaSlf4j = factory.getLogger(name)
        val viaCore = CoreLogger.of(name)

        // Same name, same registry entry: the SLF4J logger is a thin wrapper over this exact instance.
        assertThat(viaSlf4j.name).isEqualTo(viaCore.name)
        assertThat(CoreLogger.registry.get(name)).isSameInstanceAs(viaCore)
    }

    @Test
    fun getLogger_reflectsLevelChangesMadeThroughTheCoreRegistry() {
        val name = "factory.level.propagation"
        val logger = factory.getLogger(name)

        CoreLogger.registry.setLevel(name, Level.ERROR)
        assertThat(logger.isWarnEnabled).isEqualTo(false)
        assertThat(logger.isErrorEnabled).isTrue()

        CoreLogger.registry.setLevel(name, Level.TRACE)
        assertThat(logger.isTraceEnabled).isTrue()
    }

    @Test
    fun getLogger_isRegisteredInTheCoreRegistry() {
        val name = "factory.registered"

        factory.getLogger(name)

        assertThat(CoreLogger.registry.get(name)).isNotNull()
    }

    @Test
    fun getLogger_newLoggerAdoptsTheCurrentRootLevel() {
        RootLogger.Logging.level = Level.DEBUG

        // The level is snapshotted when the underlying log4k logger is first created, so this only holds
        // for a name that does not exist yet.
        val logger = factory.getLogger("factory.root.level.debug")

        assertThat(logger.isDebugEnabled).isTrue()
    }

    @Test
    fun getLogger_sameName_returnsLoggersBackedByTheSameInstance() {
        val name = "factory.same.instance"

        val first = factory.getLogger(name)
        val second = factory.getLogger(name)

        // Log4kLogger is a per-call wrapper, but both must wrap the same underlying log4k logger — so a
        // level change through the registry is visible via either handle.
        CoreLogger.registry.setLevel(name, Level.ERROR)
        assertThat(first.isWarnEnabled).isEqualTo(second.isWarnEnabled)
        assertThat(first.isErrorEnabled).isEqualTo(second.isErrorEnabled)
        assertThat(first.name).isEqualTo(second.name)
    }
}
