package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

interface MeterFactory {
    fun create(name: String): Meter
    fun get(clazz: KClass<*>): Meter = get(clazz.toName())
    fun get(name: String): Meter {
        val existing = RootLogger.Metering.meters.get(name)
        if (existing != null) return existing
        return create(name).also {
            RootLogger.Metering.meters.register(it)
        }
    }
}