package io.github.smyrgeorge.log4k.impl.registry

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.impl.extensions.withLockBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused")
class AppenderRegistry<T> {
    private val mutex = Mutex()
    private val scope = AppenderRegistryScope()
    private val appenders = mutableListOf<Appender<T>>()

    fun all(): List<Appender<T>> = mutex.withLockBlocking { appenders.toList() }
    fun get(name: String): Appender<T>? = mutex.withLockBlocking { appenders.find { it.name == name } }
    fun register(appender: Appender<T>) = mutex.withLockBlocking { appenders.add(appender) }
    fun unregister(name: String) = mutex.withLockBlocking { appenders.removeAll { it.name == name } }
    fun unregisterAll() = mutex.withLockBlocking { appenders.clear() }

    private inline fun send(crossinline f: suspend () -> Unit) {
        scope.launch { f() }
    }

    private class AppenderRegistryScope : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = EmptyCoroutineContext
    }
}