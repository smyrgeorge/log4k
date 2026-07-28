package io.github.smyrgeorge.log4k

import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

/**
 * An abstract factory class for creating and managing instances of `Tracer`.
 *
 * This class provides methods to get existing tracers or create new ones if they do not already exist.
 * Implementations of this factory must define the `create` method to provide custom tracer creation logic.
 */
abstract class TracerFactory {
    abstract fun create(name: String): Tracer
    fun get(clazz: KClass<*>): Tracer = get(clazz.toName())
    fun get(name: String): Tracer = Tracer.registry.getOrRegister(name) { create(name) }
}
