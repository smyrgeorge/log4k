package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

abstract class TracerFactory {
    abstract fun create(name: String): Tracer
    fun get(clazz: KClass<*>): Tracer = get(clazz.toName())
    fun get(name: String): Tracer {
        val existing = Tracer.registry.get(name)
        if (existing != null) return existing
        return create(name).also {
            Tracer.registry.register(it)
        }
    }
}