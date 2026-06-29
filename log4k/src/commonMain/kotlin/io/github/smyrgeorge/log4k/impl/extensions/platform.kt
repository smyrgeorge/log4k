package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

internal expect fun KClass<*>.toName(): String
internal expect fun dispatcher(): CoroutineDispatcher
internal expect fun thread(): String
internal expect fun threadId(): Int
internal expect fun platformPrintlnError(message: String)

