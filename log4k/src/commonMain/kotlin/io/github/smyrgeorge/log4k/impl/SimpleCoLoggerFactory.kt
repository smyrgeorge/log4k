package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.CoLogger
import io.github.smyrgeorge.log4k.CoLoggerFactory
import io.github.smyrgeorge.log4k.RootLogger

class SimpleCoLoggerFactory : CoLoggerFactory() {
    override fun create(name: String): CoLogger = SimpleCoLogger(name, RootLogger.Logging.level)
}