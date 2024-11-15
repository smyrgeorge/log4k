package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

abstract class MeterFactory {
    abstract fun create(name: String): Meter
    fun get(clazz: KClass<*>): Meter = get(clazz.toName())
    fun get(name: String): Meter {
        val existing = Meter.registry.get(name)
        if (existing != null) return existing
        return create(name).also {
            Meter.registry.register(it)
        }
    }
}