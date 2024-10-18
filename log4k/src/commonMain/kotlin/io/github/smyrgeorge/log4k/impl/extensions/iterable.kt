package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlin.coroutines.EmptyCoroutineContext

suspend fun <A> Iterable<A>.forEachParallel(f: suspend (A) -> Unit): Unit =
    withContext(EmptyCoroutineContext) { map { async { f(it) } }.awaitAll() }