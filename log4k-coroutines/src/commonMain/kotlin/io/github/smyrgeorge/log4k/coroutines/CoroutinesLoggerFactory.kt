package io.github.smyrgeorge.log4k.coroutines

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

@Deprecated("Use TracingContext with context parameters instead.")
abstract class CoroutinesLoggerFactory {
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
