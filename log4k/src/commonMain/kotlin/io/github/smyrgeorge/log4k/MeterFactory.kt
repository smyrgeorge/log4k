package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

/**
 * Provides an abstract definition for creating and managing instances of `Meter`.
 *
 * This class serves as a factory for creating new `Meter` objects or retrieving existing ones
 * from the registry. It supports retrieving `Meter` instances by their string name or
 * by converting a `KClass` to a name.
 *
 * Subclasses must implement the `create` method to define how a new `Meter` instance
 * is created when one does not already exist in the registry.
 */
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
