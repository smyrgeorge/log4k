package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass

internal actual fun KClass<*>.toName(): String =
    qualifiedName ?: simpleName ?: error("Could not extract the class-name of $this")

internal actual fun dispatcher(): CoroutineDispatcher = Dispatchers.IO
internal actual fun thread(): String = Thread.currentThread().name
internal actual fun threadId(): Int = Thread.currentThread().id.toInt()
internal actual fun platformPrintlnError(message: String) = println(message)
