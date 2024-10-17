package io.github.smyrgeorge.log4k.registry

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.impl.extensions.witLock
import kotlinx.coroutines.sync.Mutex

@Suppress("unused")
class AppenderRegistry {
    private val mutex = Mutex()

    private val appenders = mutableListOf<Appender>()
    fun all(): List<Appender> = mutex.witLock { appenders.toList() }
    fun get(name: String): Appender? = mutex.witLock { appenders.find { it.name == name } }
    fun register(appender: Appender) = mutex.witLock { appenders.add(appender) }
    fun unregister(name: String) = mutex.witLock { appenders.removeAll { it.name == name } }
    fun unregisterAll() = mutex.witLock { appenders.clear() }
}