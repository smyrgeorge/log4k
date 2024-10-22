package io.github.smyrgeorge.log4k.impl.extensions

import kotlin.reflect.KClass

expect fun KClass<*>.toName(): String