package io.github.smyrgeorge.log4k.slf4j

import io.github.smyrgeorge.log4k.RootLogger
import org.slf4j.ILoggerFactory
import org.slf4j.Logger

/**
 * An implementation of the [ILoggerFactory] interface that creates and manages instances of
 * Log4k loggers.
 *
 * This factory provides a bridge between the Log4k logging framework and the ILoggerFactory
 * interface, allowing for seamless integration of Log4k with existing logging infrastructure.
 */
public class Log4kILoggerFactory : ILoggerFactory {
    override fun getLogger(name: String): Logger =
        RootLogger.Logging.factory.get(name).let { Log4kLogger(it) }
}