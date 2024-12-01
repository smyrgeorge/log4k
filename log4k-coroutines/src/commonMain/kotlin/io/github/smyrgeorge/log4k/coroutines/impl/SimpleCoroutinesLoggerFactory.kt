package io.github.smyrgeorge.log4k.coroutines.impl

import io.github.smyrgeorge.log4k.RootLogger
import io.github.smyrgeorge.log4k.coroutines.CoroutinesLoggerFactory
import io.github.smyrgeorge.log4k.coroutines.Logger

class SimpleCoroutinesLoggerFactory : CoroutinesLoggerFactory() {
    override fun create(name: String): Logger = SimpleCoroutinesLogger(name, RootLogger.Logging.level)
}