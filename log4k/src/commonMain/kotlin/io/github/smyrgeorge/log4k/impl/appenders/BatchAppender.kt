package io.github.smyrgeorge.log4k.impl.appenders

import io.github.smyrgeorge.log4k.impl.extensions.chunked
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.chunked
import kotlin.time.Duration

/**
 * BatchAppender is an abstract class that extends FlowAppender to group items into batches of a specified size.
 *
 * @param T The type of item that will be batched.
 * @param size The size of each batch.
 * @param timeout Optional maximum time to wait before emitting a batch. When set, a batch is emitted
 * as soon as it reaches [size] items or [timeout] has elapsed since the first item of the batch arrived —
 * whichever comes first. When `null` (the default), batches are emitted only when they reach [size] items.
 *
 * It overrides the `setup` method of the FlowAppender class to create chunks (batches) of the specified size
 * from the incoming flow of items.
 */
abstract class BatchAppender<T>(
    private val size: Int,
    private val timeout: Duration? = null,
) : FlowAppender<List<T>, T>() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun setup(flow: Flow<T>): Flow<List<T>> =
        if (timeout == null) flow.chunked(size)
        else flow.chunked(size, timeout)
}
