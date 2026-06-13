package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

private object EmptyScope : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = EmptyCoroutineContext
}

/**
 * Launches a coroutine within an isolated scope using the given dispatcher and suspending function.
 *
 * This function provides a convenient way to initiate a coroutine on a specified dispatcher
 * to execute the provided suspendable work `f`. The lifecycle of the coroutine is independent
 * and detached from any parent coroutines or scope.
 *
 * @param dispatcher The coroutine dispatcher to use for the context of the coroutine. Defaults to `Dispatchers.Default`.
 * @param f The suspending function to be executed within the launched coroutine.
 * @return The `Job` object representing the coroutine. This can be used to monitor or cancel the coroutine.
 */
fun launch(
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    f: suspend () -> Unit
): Job = EmptyScope.launch(dispatcher) { f() }

/**
 * Continuously executes a suspendable function `f` at a specified time interval defined by `delay`.
 *
 * This method schedules the given function `f` to run indefinitely, with each execution delayed
 * by the specified duration. If an exception occurs during the execution of `f`, it will be caught
 * and the loop will continue uninterrupted.
 *
 * @param delay The duration to wait between each execution of the provided suspend function.
 * @param f The suspend function to be executed repeatedly.
 */
fun doEvery(delay: Duration, dispatcher: CoroutineDispatcher = Dispatchers.Default, f: suspend () -> Unit): Job {
    return launch(dispatcher) {
        while (true) {
            // `delay` is outside the catch so a CancellationException from cancelling the returned
            // Job propagates and stops the loop. Catching it turns cancel() into a hot 100%-CPU spin,
            // since every subsequent delay() on a cancelled job throws.
            delay(delay)
            try {
                f()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Swallow errors from the action so the periodic loop keeps ticking.
            }
        }
    }
}
