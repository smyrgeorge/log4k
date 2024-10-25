package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

expect fun KClass<*>.toName(): String
expect fun dispatcher(): CoroutineDispatcher
expect fun thread(): String
expect fun threadId(): Int
expect fun platformPrintlnError(message: String)
