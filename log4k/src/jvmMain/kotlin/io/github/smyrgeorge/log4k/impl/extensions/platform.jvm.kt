package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass

actual fun KClass<*>.toName(): String =
    qualifiedName ?: simpleName ?: error("Could not extract the class-name of $this")

actual fun dispatcher(): CoroutineDispatcher = Dispatchers.IO
actual fun thread(): String = Thread.currentThread().name
actual fun threadId(): Int = Thread.currentThread().id.toInt()
actual fun platformPrintlnError(message: String) = println(message)