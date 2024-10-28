package io.github.smyrgeorge.log4k.impl.registry

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

/**
 * A registry that manages a collection of appenders.
 *
 * @param T The type of event to be appended.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class AppenderRegistry<T> {
    private val appenders = mutableListOf<Appender<T>>()

    fun all(): List<Appender<T>> = appenders.toList()

    @Suppress("UNCHECKED_CAST")
    fun <A : Appender<*>> get(clazz: KClass<A>): A = get(clazz.toName()) as? A
        ?: error("Could not find appender with class: ${clazz.toName()}")

    fun get(name: String): Appender<T> = appenders.find { it.name == name }
        ?: error("Could not find appender with name: $name")

    fun register(appender: Appender<T>) = appenders.add(appender)
    fun unregister(name: String) = appenders.removeAll { it.name == name }
    fun unregister(appender: Appender<*>) = unregister(appender.name)
    fun unregister(clazz: KClass<*>) = unregister(clazz.toName())
    fun unregisterAll() = appenders.clear()
}