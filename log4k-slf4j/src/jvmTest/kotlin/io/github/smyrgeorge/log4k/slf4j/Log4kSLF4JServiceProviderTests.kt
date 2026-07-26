package io.github.smyrgeorge.log4k.slf4j

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import assertk.assertions.startsWith
import org.slf4j.LoggerFactory
import kotlin.test.Test

/**
 * Tests for [Log4kSLF4JServiceProvider], including the part that cannot be asserted by constructing it
 * directly: that SLF4J actually *discovers* it. The provider is published through
 * `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`, so if that resource is missing or names the
 * wrong class, `LoggerFactory` silently binds to a different provider (or a NOP one) and every log
 * statement in a consuming application disappears.
 */
class Log4kSLF4JServiceProviderTests {

    // --- Discovery through the real SLF4J entry point -------------------------------------------

    @Test
    fun slf4jBindsToTheLog4kProvider() {
        assertThat(LoggerFactory.getILoggerFactory()).isInstanceOf(Log4kILoggerFactory::class)
    }

    @Test
    fun loggerFactory_returnsLog4kLoggers() {
        val logger = LoggerFactory.getLogger("provider.discovery")

        assertThat(logger).isInstanceOf(Log4kLogger::class)
        assertThat(logger.name).isEqualTo("provider.discovery")
    }

    // --- Provider contract ----------------------------------------------------------------------

    @Test
    fun loggerFactory_isALog4kLoggerFactory() {
        assertThat(Log4kSLF4JServiceProvider().loggerFactory).isInstanceOf(Log4kILoggerFactory::class)
    }

    @Test
    fun requestedApiVersion_isAnSlf4j2Version() {
        // SLF4J compares this against its own version and warns on a mismatch.
        assertThat(Log4kSLF4JServiceProvider().requestedApiVersion).startsWith("2.")
    }

    @Test
    fun loggerFactory_isStableAcrossCalls() {
        val provider = Log4kSLF4JServiceProvider()

        assertThat(provider.loggerFactory).isSameInstanceAs(provider.loggerFactory)
    }

    @Test
    fun initialize_isANoOpAndDoesNotThrow() {
        Log4kSLF4JServiceProvider().initialize()
    }
}
