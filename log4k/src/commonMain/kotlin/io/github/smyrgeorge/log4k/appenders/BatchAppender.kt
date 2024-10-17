package io.github.smyrgeorge.log4k.appenders

import io.github.smyrgeorge.log4k.LoggingEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked

@Suppress("unused")
abstract class BatchAppender(private val size: Int) : FlowAppender<List<LoggingEvent>>() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun setup(flow: Flow<LoggingEvent>): Flow<List<LoggingEvent>> =
        flow.chunked(size)
}