package io.github.smyrgeorge.log4k.impl.appenders

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.impl.extensions.dispatcher
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * FlowAppender is an abstract class that provides an implementation of the `Appender` interface using Kotlin Flows.
 * It processes incoming events asynchronously and allows custom handling by subclassing.
 *
 * @param T The type of the transformed events.
 * @param E The type of the original events to be appended.
 *
 * This class utilizes a `Channel` to buffer the incoming events and processes them using a Coroutine Flow.
 * The event processing logic is defined in the `setup` and `handle` methods which need to be implemented by subclasses.
 */
abstract class FlowAppender<T, E> : Appender<E> {
    private val scope = FlowAppenderScope()
    private val dispatcher: CoroutineDispatcher = dispatcher()
    private val logs: Channel<E> = Channel(capacity = Channel.UNLIMITED)

    @Suppress("UNCHECKED_CAST")
    private var flow: Flow<T> = logs.receiveAsFlow().flowOn(dispatcher) as Flow<T>

    init {
        FlowAppenderScope().launch(dispatcher) {
            @Suppress("UNCHECKED_CAST")
            flow = setup(this@FlowAppender.flow as Flow<E>)
            flow.onEach { event: T -> runCatching { handle(event) } }.launchIn(this)
        }
    }

    abstract fun setup(flow: Flow<E>): Flow<T>
    abstract suspend fun handle(event: T)

    final override val name: String = this::class.toName()
    final override suspend fun append(event: E): Unit = logs.send(event)

    private class FlowAppenderScope : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = EmptyCoroutineContext
    }
}