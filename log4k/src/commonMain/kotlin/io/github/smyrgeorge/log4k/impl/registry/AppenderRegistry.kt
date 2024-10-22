package io.github.smyrgeorge.log4k.impl.registry

import io.github.smyrgeorge.log4k.Appender

@Suppress("unused")
class AppenderRegistry<T> {
    private val appenders = mutableListOf<Appender<T>>()

    fun all(): List<Appender<T>> = appenders.toList()
    fun get(name: String): Appender<T>? = appenders.find { it.name == name }
    fun register(appender: Appender<T>) = appenders.add(appender)
    fun unregister(name: String) = appenders.removeAll { it.name == name }
    fun unregisterAll() = appenders.clear()
}