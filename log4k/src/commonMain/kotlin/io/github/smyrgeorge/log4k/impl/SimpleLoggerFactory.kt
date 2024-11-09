package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.LoggerFactory
import io.github.smyrgeorge.log4k.RootLogger

class SimpleLoggerFactory : LoggerFactory() {
    override fun create(name: String): Logger = SimpleLogger(name, RootLogger.Logging.level)
}