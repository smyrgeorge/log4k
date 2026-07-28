package io.github.smyrgeorge.log4k.impl.appenders

import io.github.smyrgeorge.log4k.Appender
import io.github.smyrgeorge.log4k.impl.extensions.dispatcher
import io.github.smyrgeorge.log4k.impl.extensions.toName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
    private val dispatcher: CoroutineDispatcher = dispatcher()
    private val channel: Channel<E> = Channel(capacity = Channel.UNLIMITED)

    // Started lazily on the first append instead of in `init`: an init-launched coroutine calls
    // the subclass's `setup()` and races with the rest of the subclass's construction, so it can
    // observe not-yet-assigned constructor properties (e.g. a batch size of 0). By the first
    // append the instance is fully constructed, and `lazy` provides the happens-before edge.
    private val pipeline: Job by lazy {
        // `setup` runs here (not inside the coroutine) so a failure — e.g. an invalid subclass
        // configuration — propagates to the first `append` caller instead of silently killing
        // the background pipeline while the appender keeps accepting events.
        val processed = setup(channel.receiveAsFlow())
        FlowAppenderScope().launch(dispatcher) {
            processed
                .flowOn(dispatcher)
                .onEach { event: T -> runCatching { handle(event) } }
                .launchIn(this)
        }
    }

    final override val name: String = this::class.toName()

    final override suspend fun append(event: E) {
        pipeline // Touch to start the processing pipeline on first use.
        channel.send(event)
    }

    abstract fun setup(flow: Flow<E>): Flow<T>
    abstract suspend fun handle(event: T)

    private class FlowAppenderScope : CoroutineScope {
        override val coroutineContext: CoroutineContext
            get() = EmptyCoroutineContext
    }
}
