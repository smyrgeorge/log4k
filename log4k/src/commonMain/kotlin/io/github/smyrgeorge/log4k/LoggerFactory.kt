package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

/**
 * An abstract factory class responsible for creating and retrieving instances of the Logger class.
 *
 * This class provides methods for getting a logger instance by name or by the class reference.
 * If a logger with the specified name already exists in the registry, it is returned. Otherwise,
 * a new instance is created using the abstract `create` method and registered in the logger registry.
 */
abstract class LoggerFactory {
    abstract fun create(name: String): Logger
    fun get(clazz: KClass<*>): Logger = get(clazz.toName())
    fun get(name: String): Logger {
        val existing = Logger.registry.get(name)
        if (existing != null) return existing
        return create(name).also {
            Logger.registry.register(it)
        }
    }
}
