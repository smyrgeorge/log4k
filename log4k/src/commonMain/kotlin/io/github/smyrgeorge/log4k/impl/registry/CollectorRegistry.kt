package io.github.smyrgeorge.log4k.impl.registry

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlin.reflect.KClass

/**
 * A registry for managing and organizing `Collector` instances of a specified type.
 *
 * This class facilitates operations for registering, retrieving, and configuring collectors,
 * as well as muting and unmuting their logging capabilities. Each collector in the registry
 * is uniquely identified by its name or its class type.
 *
 * @param T The type of the collector that extends the [CollectorRegistry.Collector] interface.
 */
@OptIn(ExperimentalAtomicApi::class)
class CollectorRegistry<T> where T : CollectorRegistry.Collector {
    private val muted: AtomicReference<Set<String>> = AtomicReference(emptySet())
    private val collectors: AtomicReference<Map<String, T>> = AtomicReference(emptyMap())

    fun get(clazz: KClass<*>): T? = get(clazz.toName())
    fun get(name: String): T? = collectors.load()[name]

    fun register(collector: T) {
        if (isMuted(collector.name)) collector.mute()
        collectors.update { it + (collector.name to collector) }
    }

    /**
     * Atomically returns the collector registered under [name], creating and registering the one
     * produced by [create] when absent.
     *
     * Unlike a plain `get(name) ?: create().also(::register)` — a non-atomic check-then-act — this
     * guarantees that every concurrent caller receives the *same* instance: under a race [create]
     * may run more than once, but only one instance is ever published and returned; the losers are
     * discarded before anyone can hold on to them. (A never-published instance could otherwise keep
     * logging forever at its construction-time level, unreachable by `setLevel`/`mute`.)
     */
    fun getOrRegister(name: String, create: () -> T): T {
        get(name)?.let { return it }
        val created = create()
        if (isMuted(name)) created.mute() // same treatment as register()
        while (true) {
            val current = collectors.load()
            current[name]?.let { return it }
            if (collectors.compareAndSet(current, current + (name to created))) return created
        }
    }

    fun setLevel(clazz: KClass<*>, level: Level): Unit = setLevel(clazz.toName(), level)
    fun setLevel(name: String, level: Level) {
        get(name)?.level = level
    }

    fun mute(clazz: KClass<*>): Unit = mute(clazz.toName())
    fun mute(name: String) {
        muted.update { it + name }
        get(name)?.mute()
    }

    fun unmute(clazz: KClass<*>): Unit = unmute(clazz.toName())
    fun unmute(name: String) {
        muted.update { it - name }
        get(name)?.unmute()
    }

    fun isMuted(clazz: KClass<*>): Boolean = isMuted(clazz.toName())
    fun isMuted(name: String): Boolean = name in muted.load()

    /**
     * Represents an entity responsible for collecting and managing log messages
     * or related system events based on their logging level.
     *
     * The `Collector` interface defines functionalities for controlling the logging level,
     * muting/unmuting log output, and retrieving muted state information. Implementations
     * of this interface should allow dynamic adjustment of logging behavior.
     */
    interface Collector {
        val name: String
        var level: Level
        var levelBeforeMute: Level

        /**
         * Mutes the logger by setting its logging level to `Level.OFF`.
         *
         * This method saves the current logging level in the `levelBeforeMute` field before muting.
         * Muting an already-muted collector is a no-op, so a later [unmute] always restores the
         * level from before the first mute (a repeated mute must not overwrite it with `OFF`).
         */
        fun mute() {
            if (isMuted()) return
            levelBeforeMute = level
            level = Level.OFF
        }

        /**
         * Reverts the logger to its previous logging level before it was muted.
         *
         * This method restores the logging level stored in `levelBeforeMute` back to `level`.
         * The `levelBeforeMute` field will also be updated to reflect the current `level`.
         */
        fun unmute() {
            level = levelBeforeMute
            levelBeforeMute = level
        }

        fun isMuted(): Boolean = level == Level.OFF
    }
}
