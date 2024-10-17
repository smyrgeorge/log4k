package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun <T> Mutex.witLock(f: () -> T): T = runBlocking { withLock { f() } }