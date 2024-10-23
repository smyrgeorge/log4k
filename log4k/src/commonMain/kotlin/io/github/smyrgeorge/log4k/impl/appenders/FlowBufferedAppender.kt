package io.github.smyrgeorge.log4k.impl.appenders

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer

@Suppress("unused")
abstract class FlowBufferedAppender<T>(
    private val capacity: Int,
    private val onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
) : FlowAppender<T, T>() {
    override fun setup(flow: Flow<T>): Flow<T> =
        flow.buffer(capacity = capacity, onBufferOverflow = onBufferOverflow)
}