package io.github.smyrgeorge.log4k.impl.registry

import io.github.smyrgeorge.log4k.Level
import io.github.smyrgeorge.log4k.impl.extensions.toName
import io.github.smyrgeorge.log4k.impl.extensions.witLock
import kotlinx.coroutines.sync.Mutex
import kotlin.reflect.KClass

@Suppress("MemberVisibilityCanBePrivate", "unused")
class LoggerRegistry<T> where T : LoggerRegistry.Collector {
    private val mutex = Mutex()
    private val muted = mutableSetOf<String>()
    private val loggers = mutableMapOf<String, T>()

    fun get(clazz: KClass<*>): T? = get(clazz.toName())
    fun get(name: String): T? = mutex.witLock { loggers[name] }
    fun register(logger: T): Unit = mutex.witLock {
        fun isMuted(name: String): Boolean = name in muted
        val muted = isMuted(logger.name)
        if (muted) logger.mute()
        loggers[logger.name] = logger
    }

    fun setLevel(clazz: KClass<*>, level: Level): Unit = setLevel(clazz.toName(), level)
    fun setLevel(name: String, level: Level): Unit = mutex.witLock {
        loggers[name]?.setLevel(level)
    }

    fun mute(clazz: KClass<*>): Unit = mute(clazz.toName())
    fun mute(name: String): Unit = mutex.witLock {
        muted.add(name)
        loggers[name]?.mute()
    }

    fun unmute(clazz: KClass<*>): Unit = unmute(clazz.toName())
    fun unmute(name: String): Unit = mutex.witLock {
        muted.remove(name)
        loggers[name]?.unmute()
    }

    fun isMuted(clazz: KClass<*>): Boolean = isMuted(clazz.toName())
    fun isMuted(name: String): Boolean = mutex.witLock {
        name in muted
    }

    interface Collector {
        val name: String
        fun mute()
        fun unmute()
        fun setLevel(level: Level)
    }
}