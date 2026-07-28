package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.LoggerFactory
import io.github.smyrgeorge.log4k.RootLogger

/**
 * The default [LoggerFactory]: creates a [SimpleLogger] whose level is a **snapshot** of
 * [RootLogger.Logging.level] taken at creation time. Because instances are cached by name,
 * changing [RootLogger.Logging.level] afterwards affects only loggers created from then on —
 * adjust existing ones through [Logger.registry] (e.g. `Logger.registry.setLevel(name, level)`).
 */
class SimpleLoggerFactory : LoggerFactory() {
    override fun create(name: String): Logger = SimpleLogger(name, RootLogger.Logging.level)
}
