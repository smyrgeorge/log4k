package io.github.smyrgeorge.log4k.impl.extensions

actual fun thread(): String = Thread.currentThread().name
actual fun threadId(): Int = Thread.currentThread().id.toInt()