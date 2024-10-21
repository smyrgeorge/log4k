package io.github.smyrgeorge.log4k.impl.registry

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.impl.extensions.withLockBlocking
import kotlinx.coroutines.sync.Mutex

@Suppress("unused")
class AppenderRegistry<T> {
    private val mutex = Mutex()
    private val appenders = mutableListOf<Appender<T>>()
    fun all(): List<Appender<T>> = mutex.withLockBlocking { appenders.toList() }
    fun get(name: String): Appender<T>? = mutex.withLockBlocking { appenders.find { it.name == name } }
    fun register(appender: Appender<T>) = mutex.withLockBlocking { appenders.add(appender) }
    fun unregister(name: String) = mutex.withLockBlocking { appenders.removeAll { it.name == name } }
    fun unregisterAll() = mutex.withLockBlocking { appenders.clear() }
}