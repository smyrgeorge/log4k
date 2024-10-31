package io.github.smyrgeorge.log4k.impl.registry

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlin.reflect.KClass

@Suppress("MemberVisibilityCanBePrivate", "unused")
class CollectorRegistry<T> where T : CollectorRegistry.Collector {
    private val muted = mutableSetOf<String>()
    private val loggers = mutableMapOf<String, T>()

    fun get(clazz: KClass<*>): T? = get(clazz.toName())
    fun get(name: String): T? = loggers[name]
    fun register(logger: T) {
        fun isMuted(name: String): Boolean = name in muted
        val muted = isMuted(logger.name)
        if (muted) logger.mute()
        loggers[logger.name] = logger
    }

    fun setLevel(clazz: KClass<*>, level: Level): Unit = setLevel(clazz.toName(), level)
    fun setLevel(name: String, level: Level) {
        loggers[name]?.level = level
    }

    fun mute(clazz: KClass<*>): Unit = mute(clazz.toName())
    fun mute(name: String) {
        muted.add(name)
        loggers[name]?.mute()
    }

    fun unmute(clazz: KClass<*>): Unit = unmute(clazz.toName())
    fun unmute(name: String) {
        muted.remove(name)
        loggers[name]?.unmute()
    }

    fun isMuted(clazz: KClass<*>): Boolean = isMuted(clazz.toName())
    fun isMuted(name: String): Boolean = name in muted

    open class Collector(
        open val name: String,
        open var level: Level
    ) {
        @Suppress("LeakingThis")
        private var levelBeforeMute: Level = level

        /**
         * Mutes the logger by setting its logging level to `Level.OFF`.
         *
         * This method saves the current logging level in the `levelBeforeMute` field before muting.
         */
        fun mute() {
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