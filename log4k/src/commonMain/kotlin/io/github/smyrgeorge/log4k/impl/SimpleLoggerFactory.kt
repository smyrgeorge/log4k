package io.github.smyrgeorge.log4k.impl

import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.LoggerFactory

class SimpleLoggerFactory : LoggerFactory {
    override fun getLogger(name: String): Logger {
        val existing = RootLogger.loggers.get(name)
        if (existing != null) return existing
        return SimpleLogger(name, RootLogger.level).also {
            RootLogger.loggers.register(it)
        }
    }
}