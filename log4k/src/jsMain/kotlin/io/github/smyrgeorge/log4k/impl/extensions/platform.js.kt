package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass

internal actual fun KClass<*>.toName(): String =
    simpleName ?: error("Could not extract the class-name of $this")

internal actual fun dispatcher(): CoroutineDispatcher = Dispatchers.Unconfined
internal actual fun thread(): String = "js-main"
internal actual fun threadId(): Int = 0
internal actual fun platformPrintlnError(message: String) = println(message)
