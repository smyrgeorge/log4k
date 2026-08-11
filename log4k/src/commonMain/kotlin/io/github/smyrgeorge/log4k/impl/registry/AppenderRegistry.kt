package io.github.smyrgeorge.log4k.impl.registry

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlin.reflect.KClass

/**
 * A registry for managing multiple appenders of type T.
 *
 * This class allows for the registration, retrieval, and management of
 * appenders, which are responsible for appending events. These appenders
 * can be registered by their name or class type and can be unregistered
 * individually or all at once.
 *
 * @param T The type of event that the appenders handle.
 */
@OptIn(ExperimentalAtomicApi::class)
class AppenderRegistry<T> {
    private val appenders: AtomicReference<List<Appender<T>>> = AtomicReference(emptyList())

    fun all(): List<Appender<T>> = appenders.load()

    @Suppress("UNCHECKED_CAST")
    fun <A : Appender<*>> get(clazz: KClass<A>): A = get(clazz.toName()) as? A
        ?: error("Could not find appender with class: ${clazz.toName()}")

    fun get(name: String): Appender<T> = all().find { it.name == name }
        ?: error("Could not find appender with name: $name")

    fun register(appender: Appender<T>) {
        appenders.update { it + appender }
    }

    /**
     * Registers [appender], by default making it the *only* appender: with [unregisterOthers]
     * enabled (the default) every previously registered appender — a platform default included —
     * is unregistered in the same atomic step, so no event is ever delivered to both the old and
     * the new set. Pass `unregisterOthers = false` to keep the existing appenders and register
     * [appender] alongside them.
     *
     * @param appender The appender to install.
     * @param unregisterOthers Whether to unregister every other appender first (default `true`).
     * @return [appender] itself, e.g. for a later [unregister].
     */
    fun <A : Appender<T>> install(appender: A, unregisterOthers: Boolean = true): A {
        appenders.update { current -> if (unregisterOthers) listOf(appender) else current + appender }
        return appender
    }

    fun unregister(name: String): Boolean {
        // `update`'s transform may run several times under contention; `removed` reflects the last
        // (successful) attempt, which is the one that was actually published.
        var removed = false
        appenders.update { current ->
            val next = current.filter { it.name != name }
            removed = next.size != current.size
            next
        }
        return removed
    }

    fun unregister(appender: Appender<*>): Boolean = unregister(appender.name)
    fun unregister(clazz: KClass<*>): Boolean = unregister(clazz.toName())

    fun unregisterAll() {
        appenders.store(emptyList())
    }
}
