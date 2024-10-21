package io.github.smyrgeorge.log4k.impl.appenders

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.Logger
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("unused")
abstract class FlowAppender<T, E> : Appender<E> {
    private val log = Logger.of(this::class)
    private val logs: Channel<E> = Channel(capacity = Channel.UNLIMITED)

    @Suppress("UNCHECKED_CAST")
    private var flow: Flow<T> = logs.receiveAsFlow().flowOn(Dispatchers.IO) as Flow<T>

    init {
        FlowAppenderScope().launch(Dispatchers.IO) {
            @Suppress("UNCHECKED_CAST")
            flow = setup(this@FlowAppender.flow as Flow<E>)
            flow.onEach { event: T -> runCatching { append(event) } }.launchIn(this)
        }
    }

    final override val name: String = this::class.toName()
    final override fun append(event: E) {
        runBlocking { logs.send(event) }
    }

    abstract fun setup(flow: Flow<E>): Flow<T>
    abstract suspend fun append(event: T)

    private class FlowAppenderScope : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = EmptyCoroutineContext
    }
}