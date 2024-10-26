package io.github.smyrgeorge.log4k.impl.appenders

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked

/**
 * BatchAppender is an abstract class that extends FlowAppender to group items into batches of a specified size.
 *
 * @param T The type of item that will be batched.
 * @param size The size of each batch.
 *
 * It overrides the `setup` method of the FlowAppender class to create chunks (batches) of the specified size
 * from the incoming flow of items.
 */
abstract class BatchAppender<T>(private val size: Int) : FlowAppender<List<T>, T>() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun setup(flow: Flow<T>): Flow<List<T>> = flow.chunked(size)
}