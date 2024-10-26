package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

abstract class LoggerFactory {
    abstract fun create(name: String): Logger
    fun get(clazz: KClass<*>): Logger = get(clazz.toName())
    fun get(name: String): Logger {
        val existing = RootLogger.Logging.loggers.get(name)
        if (existing != null) return existing
        return create(name).also {
            RootLogger.Logging.loggers.register(it)
        }
    }
}