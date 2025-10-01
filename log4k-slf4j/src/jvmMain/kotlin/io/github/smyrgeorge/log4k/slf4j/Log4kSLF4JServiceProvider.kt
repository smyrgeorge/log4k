package io.github.smyrgeorge.log4k.slf4j

import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.helpers.BasicMDCAdapter
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.NOP_FallbackServiceProvider.REQUESTED_API_VERSION
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider

/**
 * Service provider for integrating Log4k with SLF4J.
 *
 * This class implements the [org.slf4j.spi.SLF4JServiceProvider] interface,
 * providing Log4k based implementations for SLF4J's logger factory, marker factory,
 * and MDC adapter.
 *
 * The service provider initializes and manages the following components:
 * - A custom [ILoggerFactory] implemented by [Log4kILoggerFactory]
 * - A basic [IMarkerFactory] implemented by [BasicMarkerFactory]
 * - A basic [MDCAdapter] implemented by [BasicMDCAdapter]
 *
 * Usage of this provider ensures that all SLF4J logging calls are handled
 * using the Log4k logging framework.
 */
public class Log4kSLF4JServiceProvider : SLF4JServiceProvider {
    private val loggerFactory: ILoggerFactory = Log4kILoggerFactory()
    private val markerFactory: IMarkerFactory = BasicMarkerFactory()
    private val mdcAdapter: MDCAdapter = BasicMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = loggerFactory
    override fun getMarkerFactory(): IMarkerFactory = markerFactory
    override fun getMDCAdapter(): MDCAdapter = mdcAdapter
    override fun getRequestedApiVersion(): String = REQUESTED_API_VERSION

    override fun initialize() {
        // No-op
    }
}
