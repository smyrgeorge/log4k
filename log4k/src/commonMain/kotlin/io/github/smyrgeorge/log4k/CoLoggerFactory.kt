package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

abstract class CoLoggerFactory {
    abstract fun create(name: String): CoLogger
    fun get(clazz: KClass<*>): CoLogger = get(clazz.toName())
    fun get(name: String): CoLogger {
        val existing = RootLogger.Logging.coLoggers.get(name)
        if (existing != null) return existing
        return create(name).also {
            RootLogger.Logging.coLoggers.register(it)
        }
    }
}