package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass

actual fun KClass<*>.toName(): String =
    simpleName ?: error("Could not extract the class-name of $this")

actual fun dispatcher(): CoroutineDispatcher = Dispatchers.Default
actual fun thread(): String = "wasm"
actual fun threadId(): Int = 0
actual fun platformPrintlnError(message: String) = println(message)