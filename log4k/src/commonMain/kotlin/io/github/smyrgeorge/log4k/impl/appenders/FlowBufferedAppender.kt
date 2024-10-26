package io.github.smyrgeorge.log4k.impl.appenders

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer

/**
 * FlowBufferedAppender is an abstract class that extends `FlowAppender` to provide buffering
 * capabilities for processing events using Kotlin Flows.
 *
 * @param T The type of the events to be processed.
 * @param capacity The maximum size of the buffer.
 * @param onBufferOverflow Specifies the behavior when the buffer overflows.
 *                         Defaults to `BufferOverflow.DROP_OLDEST`.
 */
abstract class FlowBufferedAppender<T>(
    private val capacity: Int,
    private val onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
) : FlowAppender<T, T>() {
    override fun setup(flow: Flow<T>): Flow<T> =
        flow.buffer(capacity = capacity, onBufferOverflow = onBufferOverflow)
}