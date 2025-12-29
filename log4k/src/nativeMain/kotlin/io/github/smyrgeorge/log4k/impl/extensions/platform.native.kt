package io.github.smyrgeorge.log4k.impl.extensions

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.posix.fprintf
import platform.posix.stderr
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.Worker
import kotlin.reflect.KClass

actual fun KClass<*>.toName(): String =
    qualifiedName ?: simpleName ?: error("Could not extract the class-name of $this")

actual fun dispatcher(): CoroutineDispatcher = Dispatchers.IO
actual fun thread(): String = "native-${threadId()}"

@OptIn(ObsoleteWorkersApi::class)
actual fun threadId(): Int = Worker.current.id

@OptIn(ExperimentalForeignApi::class)
actual fun platformPrintlnError(message: String) {
    fprintf(stderr, "%s\n", message)
}
