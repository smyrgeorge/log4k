package io.github.smyrgeorge.log4k.impl.extensions

import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.Worker

actual fun thread(): String = "native-${threadId()}"

@OptIn(ObsoleteWorkersApi::class)
actual fun threadId(): Int = Worker.current.id