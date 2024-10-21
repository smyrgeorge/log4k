package io.github.smyrgeorge.log4k.impl.appenders

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked

@Suppress("unused")
abstract class BatchAppender<T>(private val size: Int) : FlowAppender<List<T>, T>() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun setup(flow: Flow<T>): Flow<List<T>> = flow.chunked(size)
}