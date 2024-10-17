package io.github.smyrgeorge.log4k.impl.extensions

import kotlin.reflect.KClass

fun KClass<*>.toName(): String = qualifiedName ?: simpleName ?: "Unknown"