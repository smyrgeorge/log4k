package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual fun dispatcher(): CoroutineDispatcher = Dispatchers.IO