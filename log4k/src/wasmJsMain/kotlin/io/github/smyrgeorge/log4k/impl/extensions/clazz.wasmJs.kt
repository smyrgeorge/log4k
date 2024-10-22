package io.github.smyrgeorge.log4k.impl.extensions

import kotlin.reflect.KClass

actual fun KClass<*>.toName(): String =
    simpleName ?: error("Could not extract the class-name of $this")