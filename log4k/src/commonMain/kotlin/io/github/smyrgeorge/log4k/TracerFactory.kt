package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

interface TracerFactory {
    fun create(name: String): Tracer
    fun get(clazz: KClass<*>): Tracer = get(clazz.toName())
    fun get(name: String): Tracer {
        val existing = RootLogger.Tracing.tracers.get(name)
        if (existing != null) return existing
        return create(name).also {
            RootLogger.Tracing.tracers.register(it)
        }
    }
}